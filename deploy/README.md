# 部署手册（Linux 虚拟机 / 云服务器）

本目录是在官方源码基础上补的部署脚本。目标：在一台干净的 Linux 机器上把博客前台 + 管理后台 + 后端 + MySQL 跑起来。

## 0. 结论先说

`http://mrzym.top` 的线上产物，与 Gitee 上这个仓库（MIT 协议）是同一份代码：

| 证据 | 值 |
|---|---|
| 线上 `index.html` 的 `Last-Modified` | 2025-10-20 12:54:19 |
| 仓库最后一次提交 | 2025-10-20（`57d1429 feat: 更新代码`） |
| 线上打包入口 | `./static/js/index-<hash>.js` + `./static/css/index-<hash>.css` |
| 本地 `vite build` 产物入口 | 同样是 `./static/js/index-<hash>.js`（`base: "./"` 配置一致） |
| 路由集合 | 线上压缩包还原出的 16 条路由，与 `blog-v3/src/router/index.js` 完全一致 |
| 接口前缀 | 前端统一请求 `/api/...`，nginx 剥掉 `/api` 后转给 Koa |

所以不需要照着压缩产物重写前端，直接用这份源码部署即可；改功能也改得动。

## 1. 组件与端口

| 组件 | 目录 | 端口 | 说明 |
|---|---|---|---|
| 博客前台 | `blog-v3` | — | Vue3 + Vite + Element Plus + Pinia + Tailwind |
| 管理后台 | `blog-v3-admin` | — | pureadmin 模板，构建需 pnpm |
| 后端 | `blog-server` | 8888 / 8889 | Node + Koa + Sequelize；8889 是 WebSocket |
| 数据库 | — | 3306 | MySQL 8，`sql/online_blog.sql` 共 18 张表 |
| 对象存储 | — | 9000 / 9999 | MinIO，`UPLOADTYPE=minio` 时用；默认 `local` 可不启用 |
| 网易云音乐 | — | 3000 | 音乐页依赖 `binaryify/netease_cloud_music_api` |

前端产物必须构建出来放到 `blog-server/ui/blog/dist` 与 `blog-server/ui/admin/dist`，这是 `docker-compose.yml` 里 nginx 的挂载路径。

## 2. 方案 A：Docker 一键（推荐）

要求：Docker Engine + compose 插件，机器能访问 npm 镜像源。

```bash
# 1) 把整个仓库目录拷到服务器，例如 /opt/blog
sudo mkdir -p /opt/blog && sudo chown -R $USER /opt/blog
#    （git clone 也行：git clone https://gitee.com/mrzym/stable-version-of-blog.git /opt/blog）

# 2) 一键部署
cd /opt/blog
bash deploy/setup-docker.sh
```

脚本做四件事：构建前台（可选后台）→ 把 `blog-server/.env` 的 `MYSQL_HOST` 改成 compose 服务名 `db` → `docker compose up -d --build` → 轮询 `/api/config` 等后端就绪。

只想先跑前台：

```bash
SKIP_ADMIN=1 bash deploy/setup-docker.sh
```

访问：

- 博客前台 `http://<机器IP>/`
- 管理后台 `http://<机器IP>/admin/`（账号 `admin`，密码取 `.env` 的 `ADMIN_PASSWORD`）
- 接口文档 `http://<机器IP>:8888/swagger`
- MinIO 控制台 `http://<机器IP>:9999/`（root / 123456）

数据库首次启动会自动执行 `blog-server/sql/online_blog.sql`（compose 把它挂到 `/docker-entrypoint-initdb.d`）。**注意：这个 SQL 里有 `DROP TABLE IF EXISTS`，重复导入会清库。**

## 3. 方案 B：手工部署（不用 Docker）

以 Ubuntu 22.04 为例，路径统一用 `/opt/blog`。

### 3.1 依赖

```bash
# Node 18（项目 README 声明的版本）
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs nginx mysql-server
sudo npm i -g pm2 pnpm@7
```

### 3.2 数据库

```bash
sudo mysql -e "CREATE DATABASE IF NOT EXISTS online_blog DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
sudo mysql online_blog < /opt/blog/blog-server/sql/online_blog.sql
sudo mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '你的密码'; FLUSH PRIVILEGES;"
```

### 3.3 后端

```bash
cd /opt/blog/blog-server
npm install --registry=https://registry.npmmirror.com
# 改 .env：MYSQL_HOST=127.0.0.1、MYSQL_PASSWORD=你的密码、MYSQL_DB=online_blog
#          JWT_SECRET 换成长随机串、ADMIN_PASSWORD 设一个强密码
pm2 start app.js --name blog-server
pm2 save && pm2 startup      # 开机自启
```

托管方式二选一：上面的 pm2，或 `deploy/manual/blog-server.service`（systemd）。

### 3.4 前端

```bash
cd /opt/blog
bash deploy/build-frontends.sh          # 产物直接落到 blog-server/ui/*
sudo mkdir -p /opt/blog/ui
sudo cp -r blog-server/ui/blog  /opt/blog/ui/blog
sudo cp -r blog-server/ui/admin /opt/blog/ui/admin
```

### 3.5 Nginx

```bash
sudo cp /opt/blog/deploy/manual/nginx-blog.conf /etc/nginx/conf.d/blog.conf
sudo nginx -t && sudo systemctl reload nginx
```

音乐页需要网易云接口，可以用 Docker 单独起一个，或直接改前端让 `/wapi/` 指向你自己的实例：

```bash
docker run -d --name netease -p 3000:3000 binaryify/netease_cloud_music_api
```

不打算用音乐页的话，`/wapi/` 那条 location 留着也不影响其他功能。

## 4. 上传方式（UPLOADTYPE）

`.env` 里四选一：

| 值 | 行为 | 适用 |
|---|---|---|
| `local` | 存到 `blog-server/src/upload/local` | 单机自用，最简单（默认） |
| `minio` | 传到 MinIO，配合 compose 的 minio 服务 | 想要对象存储、图片走独立域名 |
| `qiniu` | 七牛云，需 `ACCESSKEY/SECRETKEY/BUCKET/DOMAIN` | 有备案域名 |
| `online` | 存到服务器本地目录 | 和 local 类似，按自己的路径习惯 |

注意：`.env` 里 `UPLOADTYPE` 的值带引号（`'local'`），切换时保持这个格式。

## 5. 上线前必改

- `ADMIN_PASSWORD`、`JWT_SECRET`：默认值等于没有鉴权。
- MySQL root 密码：`docker-compose.yml` 里写死 `12345678`，改掉并同步 `.env`。
- MinIO `MINIO_ROOT_PASSWORD`：默认 `123456`。
- 不要把 3306 / 9000 / 9999 直接暴露到公网，用防火墙或安全组只放 80/443。
- 正式环境建议在 nginx 前面加 HTTPS（certbot 或云厂商证书），`blog-v3` 的接口用相对路径，跟着域名走，不用改代码。

## 6. 常见问题

**页面白屏、控制台报 404 找 `static/js/*.js`**
前端没构建或产物没放到 `blog-server/ui/blog/dist`。跑 `deploy/build-frontends.sh` 确认 `ui/blog/dist/index.html` 存在。

**接口全 404**
nginx 少配了 `/api/` 反向代理，或 `proxy_pass` 忘了结尾的 `/`。正确写法是 `proxy_pass http://127.0.0.1:8888/;`（带斜杠才会剥掉 `/api`）；Docker 里是 `http://blog-server:8888/`。

**后端起不来，日志报 `ECONNREFUSED` / `Access denied`**
`MYSQL_HOST` 写错。Docker 里必须是 compose 服务名 `db`；手工部署用 `127.0.0.1`。

**管理后台 404 或样式丢失**
`blog-v3-admin` 是 hash 路由 + 相对路径（`VITE_PUBLIC_PATH = ./`），必须挂在 `/admin/` 而不是根路径。纯静态服务器也要能回退到 `/admin/index.html`。

**首页没有数据但接口正常**
线上数据里的图片/头像都是 `http://img.mrzym.top/...` 绝对地址，换库之后这些图会 404，属于数据问题不是部署问题。改用自己上传后重新生成。

**构建报 `JavaScript heap out of memory`**
前台构建内存不够，`export NODE_OPTIONS=--max-old-space-size=4096` 再跑。

## 7. 验证状态

- ✅ 前台 `npm install` + `npm run build` 已在本地实际跑通，产物 98 个文件 / 3.8 MB，入口引用与线上一致。
- ✅ 路由表、`/api` 接口清单、`vite.config.js` 代理规则、`nginx.conf` 转发规则均已逐项核对。
- ⚠️ Docker compose 全栈未在本机验证（本机没有 Docker），脚本按仓库自带的 `docker-compose.yml` 与 `nginx.conf` 编写。
- ⚠️ 管理后台（`blog-v3-admin`）构建未实测，需要 pnpm 7；失败时用 `SKIP_ADMIN=1` 只部署前台。

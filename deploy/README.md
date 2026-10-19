# 部署手册（Java 后端 + 两个前端）

本仓库现在只保留 **Spring Boot 后端**（`blog-java-server`）和两个前端（`blog-v3`、`blog-v3-admin`）。
Node/Koa 版后端目录已移除；如果机器上还留着旧的 Node 部署，切换前先停掉（端口会冲突）。

Java 版的完整配置项、手工分步命令和排查表在 [`deploy/java/README.md`](java/README.md)。

## 1. 组件与端口

| 组件 | 目录 | 端口 | 说明 |
|---|---|---|---|
| 博客前台 | `blog-v3` | — | Vue3 + Vite，构建产物在 `ui/blog/dist` |
| 管理后台 | `blog-v3-admin` | — | pureadmin，构建产物在 `ui/admin/dist` |
| Java 后端 | `blog-java-server` | 8888 / 8889 | Spring Boot 3.2 + MyBatis-Plus；8888 是 API，8889 是 WebSocket |
| 数据库 | — | 3306 | MySQL 8，`blog-java-server/sql/online_blog.sql` 共 18 张表 |
| 对象存储 | — | — | 阿里云 OSS（`UPLOADTYPE=oss`），也可本地落盘 |
| 网易云音乐 | — | 3000 | 可选，音乐页依赖 `binaryify/netease_cloud_music_api` |

**源码目录可以放在任意位置**（例如 `/root/my-blog`），脚本会按自身位置自动定位仓库根。
`/opt/blog` 和 `/var/www/blog` 只是**非 Docker 手工部署的安装目录**（jar/upload、前端静态文件），
不是源码必须放的位置。Docker 部署全部在容器内，不使用这两个宿主机目录。

前端构建：

```bash
bash deploy/build-frontends.sh
# 产物：ui/blog/dist、ui/admin/dist
```

## 2. 前置要求

- Linux（Ubuntu 22.04 / Debian 12 为验证目标）
- Docker 路线：Docker Engine + `docker compose`（v2）或 `docker-compose`
- 源码构建前端：宿主机 Node 18/20、pnpm 7（脚本会尝试启用）
- 手工路线：脚本会按需 apt 安装 JDK 17、Maven、Nginx、MySQL、Node

> 如果虚拟机没有 Node/Maven（例如 CentOS 7 的 glibc 太老），可使用单独打好的预构建 dist 包
> （内含 `ui` 下的两个前端 dist 和 `app.jar`），不需要在虚拟机上构建。

## 3. 方案 A：Docker 全栈（推荐）

要求宿主机 80 / 8888 / 8889 空闲。

```bash
cd /path/to/my-blog
bash deploy/setup-java-docker.sh
```

脚本流程：

1. 构建前端到 `ui/`（已有产物可 `SKIP_FRONTEND=1`）；
2. 首次生成 `deploy/java/.env`，随机生成 MySQL 密码、`JWT_SECRET`、`ADMIN_PASSWORD`；
3. `docker compose up -d --build`：MySQL + Java 后端 + Nginx；
4. 轮询 `http://127.0.0.1:8888/config`，就绪后打印访问地址和 `admin` 密码。

拆开手动执行：

```bash
cd /path/to/my-blog
bash deploy/build-frontends.sh
cd deploy/java
cp .env.example .env && vi .env
docker compose --env-file .env up -d --build
docker compose --env-file .env logs -f blog-java-server
```

访问：

- 前台 `http://<IP>/`
- 后台 `http://<IP>/admin/`（账号 `admin`）
- Swagger `http://<IP>:8888/swagger`
- WebSocket `ws://<IP>:8889/`

相关文件：`deploy/java/Dockerfile`、`docker-compose.yml`、`nginx.conf`、`.env.example`。

数据库：

- MySQL 8.0 容器，库名默认 `online_blog`，应用账号默认 `blog`；
- 首次创建数据卷时自动导入 `blog-java-server/sql/online_blog.sql`（只导入主库结构，不导入 seed）；
- SQL 含 `DROP TABLE IF EXISTS`，只在空数据卷执行一次；
- 改了 `MYSQL_DB` / `MYSQL_USER` / `MYSQL_PASSWORD` 后，旧数据卷不会自动跟着改，需要 `ALTER USER` 或
  `docker compose down -v` 重建（会清库）。

可选音乐接口：

```bash
cd deploy/java
docker compose --env-file .env --profile music up -d
```

## 4. 方案 B：非 Docker 手工部署

要求 Ubuntu 22.04 / Debian 12，脚本会按需 apt 安装依赖。

```bash
cd /path/to/my-blog
bash deploy/setup-java-manual.sh
```

默认安装结果：

| 项 | 位置 |
|---|---|
| jar | `/opt/blog/blog-server.jar` |
| 前端 | `/var/www/blog/{blog,admin}` |
| 上传文件 | `/opt/blog/upload/{local,online}`（`www-data` 可写） |
| 环境变量 | `/etc/blog-java/blog-java.env`（600） |
| systemd | `blog-java-server.service` |
| nginx | `/etc/nginx/conf.d/blog.conf` |

常用开关：

| 变量 | 作用 |
|---|---|
| `SKIP_DEPS=1` | 跳过 apt 安装（依赖已装好） |
| `SKIP_FRONTEND=1` | 复用 `ui/` 下已有前端产物 |
| `SKIP_BUILD=1` | 复用 `blog-java-server/target` 下已有 jar |
| `SKIP_DB=1` | 不建库/导入（MySQL 已手工准备好） |
| `SKIP_NGINX=1` | 不覆盖现有 nginx 配置 |
| `RUN_TESTS=1` | 构建时跑单元测试 |
| `INSTALL_DIR` / `WEB_DIR` / `RUN_USER` | 覆盖安装目录、前端目录、运行用户 |

MySQL root 是密码登录时：

```bash
MYSQL_ROOT_PASSWORD='root密码' bash deploy/setup-java-manual.sh
```

手工分步（不用脚本）的完整命令见 [`deploy/java/README.md`](java/README.md)。

## 5. 上传方式

Java 版只支持 `oss` / `local` / `online`（没有 minio、qiniu）：

| 值 | 行为 |
|---|---|
| `oss` | 上传到阿里云 OSS（生产推荐），返回公网 URL |
| `local` | 落盘 `upload/local`，返回 `http://127.0.0.1:8888/local/...`，仅本机浏览器可用 |
| `online` | 落盘 `upload/online`，返回 `/online/...` |

OSS 配置在 `deploy/java/.env` 或 `/etc/blog-java/blog-java.env` 的 `OSS_*` 变量里；
没有 OSS 密钥时上传会返回 `100006`。

## 6. 上线前必改

- `ADMIN_PASSWORD`、`JWT_SECRET`：脚本首次会随机生成，不要用示例值。
- MySQL 密码：Docker 版由脚本随机生成；手工版在 `/etc/blog-java/blog-java.env`。
- OSS 密钥：按需配置，不要把真实 AccessKey 提交到仓库。
- 不要把 3306 直接暴露到公网；只放行 80/443（以及需要直连的 8888/8889）。
- 正式环境建议在 nginx 前加 HTTPS；前端接口用相对路径，跟着域名走。
- `application-local.yml` 不要进部署包：Docker 构建会删，手工脚本在临时目录构建时剔除；
  即使误带，`SPRING_DATASOURCE_*` 环境变量也会覆盖数据库配置。
- 聊天 WebSocket 前端地址目前写死为 `ws://localhost:8889/`，远程访问需要改成
  `ws://<域名>/ws/` 后重新构建前端。
- 音乐页可选：没有 3000 端口的网易云接口时 `/wapi/` 返回 502，不影响其他页面。

## 7. 常见问题

**页面白屏、404 找 `static/js/*.js`**
前端没构建或产物不在 `ui/blog/dist`。跑 `deploy/build-frontends.sh`，确认 `ui/blog/dist/index.html` 存在。

**接口全 404**
nginx 少 `/api/` 反向代理，或 `proxy_pass` 少了结尾 `/`。正确是 `proxy_pass http://127.0.0.1:8888/;`
（带斜杠才会剥掉 `/api`）；Docker 里是 `http://blog-java-server:8888/`。

**后端起不来 / 日志报数据库连接失败**
Docker 里 `MYSQL_HOST` 必须是 `db`；手工是 `127.0.0.1`。检查 `.env` / `blog-java.env` 的
`SPRING_DATASOURCE_URL`、账号密码、MySQL 是否启动。

**管理后台 404 或样式丢失**
后台是 hash 路由 + 相对路径，必须挂在 `/admin/`，nginx 用 `alias` + `try_files` 回退到 `/admin/index.html`。

**上传返回 100006**
`UPLOADTYPE` 与 OSS/本地配置不匹配；Java 版不支持 minio/qiniu。

**访问几百次后整站 429**
限流按 `remoteAddress` 计数；经过 nginx 时所有请求看起来都来自代理 IP。生产可加
`SERVER_FORWARD_HEADERS_STRATEGY=native` 让 Tomcat 读 `X-Forwarded-For`（会偏离现网行为，评估后再用）。

## 8. 验证状态

- ✅ Java 后端本身已在本机完成 `mvn package` 与 103/103 接口实现验证。
- ✅ 两个前端均已在本地实际构建成功（blog 98 个文件 / 3.8 MB，admin 334 个文件 / 5.8 MB）。
- ✅ 部署脚本已做 `bash -n` 语法检查，compose YAML 解析通过。
- ⏳ Docker / 虚拟机全栈运行尚未实测，等实际部署结果。

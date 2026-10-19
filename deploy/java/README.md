# Java 后端部署手册（Spring Boot）

`blog-java-server` 是原 Node/Koa 后端的 Java 重写，接口契约与前端保持一致（Node 版已从仓库移除）。
本文只覆盖部署，接口/契约细节见 [`blog-java-server/README.md`](../../blog-java-server/README.md)。

两条部署路线，二选一：

| 路线 | 入口脚本 | 适用 |
|---|---|---|
| Docker 全栈（MySQL + Java + Nginx） | `deploy/setup-java-docker.sh` | 干净虚拟机，想一条命令起全栈 |
| 非 Docker 手工部署（systemd + 本机 MySQL + Nginx） | `deploy/setup-java-manual.sh` | 机器上已有 MySQL/Nginx，或不想用 Docker |

> 如果机器上还留着旧的 Node 后端/旧栈，先停掉：都会占 80 / 8888 / 8889，否则端口冲突或请求打到旧后端。

---

## 0. 源码放哪里、只部署 Java 版要传什么

### 0.1 源码路径

- 仓库可以放在**任意目录**，例如 `/root/my-blog`。两个脚本都用 `BASH_SOURCE` 自己推导仓库根目录，
  不要求放在 `/opt/blog`。
- `/opt/blog` / `/var/www/blog` 只是**非 Docker 手工部署的安装目录**（jar、upload、前端静态文件），
  和源码目录无关。脚本会把构建产物从源码目录复制过去。
- 安装目录不放在 `/root` 下的原因：systemd 服务以 `www-data` 运行，nginx worker 也是低权限用户；
  `/root` 默认 700，低权限用户无法进入。Docker 版全部在容器里，不涉及这些宿主机目录。
- 如果源码放在 `/root/my-blog`：构建阶段需要 root 或 `sudo`（普通用户读不到 `/root`）；
  手工脚本的构建产物会写到 `target/`、`ui/`，属主是运行脚本的用户，root 构建时属主是 root，不影响后续部署。
- 想换安装目录：
  ```bash
  INSTALL_DIR=/srv/blog WEB_DIR=/srv/www/blog bash deploy/setup-java-manual.sh
  ```

### 0.2 只部署 Java 版 + 两个前端，最小上传清单

上传到虚拟机后保持这样的目录结构（仓库根目录，例如 `/root/my-blog`）：

```text
my-blog/
├── .dockerignore                 # Docker 路线需要（隐藏文件，别漏）
├── deploy/                       # 全部部署脚本和模板
├── blog-java-server/             # Java 后端源码 + sql/
├── blog-v3/                      # 博客前台源码（如果要在虚拟机上构建）
└── blog-v3-admin/                # 管理后台源码（如果要在虚拟机上构建）
```

| 路径 | 是否必传 | 说明 |
|---|---|---|
| `deploy/` | ✅ 必传 | `setup-java-docker.sh`、`setup-java-manual.sh`、`build-frontends.sh`、模板 |
| `blog-java-server/` | ✅ 必传 | 排除 `target/`（除非用预构建 jar）、`src/main/resources/application-local.yml`（本地密钥，不要传） |
| `.dockerignore` | Docker 必传 | 位于仓库根目录的隐藏文件，用于缩小构建上下文并排除本地密钥 |
| `blog-v3/` | 构建前端时必传 | 排除 `node_modules/`、`dist/` |
| `blog-v3-admin/` | 构建前端时必传 | 排除 `node_modules/`、`dist/` |
| `ui/{blog,admin}/dist` | 预构建前端时传 | 不想在虚拟机上装 Node 时，把本地构建好的两个 `dist` 放这里，运行时加 `SKIP_FRONTEND=1` |
| `blog-java-server/target/blog-server-*.jar` | 预构建 jar 时传 | 不想在虚拟机上装 Maven 时使用，运行时加 `SKIP_BUILD=1` |

另外不要上传：`node_modules/`、`dist/`、`target/`（预构建 jar 除外）、`.git/`、`.idea/`、
`src/main/resources/application-local.yml`。这些目录要么会在虚拟机上重新生成，要么含本地密码/密钥。

> 注意两个**隐藏文件**：仓库根目录的 `.dockerignore`，以及 `deploy/java/.env.example`
> （Docker 脚本首次运行会复制它来生成 `.env`）。用 WinSCP/资源管理器上传时记得显示隐藏文件。

几个实际组合：

1. **Docker + 虚拟机上构建前端**：传上表前 5 项（`deploy`、`blog-java-server`、两个前端源码、`.dockerignore`）。  
   宿主机只需要 Node 18/20 + Docker；JDK/Maven 在构建镜像里。
2. **Docker + 前端本地构建**：传 `deploy`、`blog-java-server`、`.dockerignore`，再传
   `ui/blog/dist`、`ui/admin/dist`，运行：
   ```bash
   SKIP_FRONTEND=1 bash deploy/setup-java-docker.sh
   ```
3. **非 Docker + 全部在虚拟机上构建**：传前 5 项，运行 `bash deploy/setup-java-manual.sh`；
   脚本会 apt 安装 JDK 17、Maven、Nginx、MySQL、Node。
4. **非 Docker + 只传预构建产物**：传 `deploy`、`blog-java-server/pom.xml` + `sql/` + `target/*.jar`、
   两个前端的 `dist`，运行：
   ```bash
   SKIP_DEPS=1 SKIP_FRONTEND=1 SKIP_BUILD=1 bash deploy/setup-java-manual.sh
   ```
   （`SKIP_DEPS=1` 时 JDK17、Nginx、MySQL、curl 仍需虚拟机上已有。）

> `build-frontends.sh` 的输出路径是 `ui/{blog,admin}/dist`，没有这个目录时会自动创建。
> 仓库已移除 Node 后端，不再需要 `blog-server/` 目录。

---

## 1. 端口与目录约定

| 项 | Java 版 | 说明 |
|---|---|---|
| 主端口 | `APP_PORT`，部署脚本默认设为 **8888** | 应用自身默认 8088，脚本显式覆盖成 8888，这样现有 nginx / 前端不用改 |
| WebSocket | `CHAT_WS_PORT`，默认 **8889** | 聊天用，独立端口 |
| 前端产物 | `ui/blog/dist`、`ui/admin/dist` | 由 `deploy/build-frontends.sh` 生成 |
| Docker 数据卷 | `db_data`、`java_upload` | MySQL 数据、本地上传文件 |
| 手工部署后端目录 | `/opt/blog` | jar、upload；可用 `INSTALL_DIR` 覆盖 |
| 手工部署前端目录 | `/var/www/blog` | nginx 静态文件；可用 `WEB_DIR` 覆盖（`/opt` 在 Ubuntu 上可能被 AppArmor 拒绝） |
| 手工部署环境变量 | `/etc/blog-java/blog-java.env` | 权限 600 |
| 手工部署 nginx 模板 | `deploy/manual/java/nginx-blog-java.conf` | 默认前端 `/var/www/blog`、后端 8888/8889 |
| 手工部署服务 | `blog-java-server.service` | systemd |

Nginx 的 `/api/` 会把前缀剥掉后转给 8888，`/ws/` 转给 8889。Docker 版直接挂载 `ui/`；
手工版默认放在 `/var/www/blog`（避开 AppArmor），脚本会自动生成对应的 nginx 配置。

---

## 2. 方案 A：Docker 全栈（推荐）

### 2.1 前置要求

- Linux（Ubuntu 22.04 / Debian 12 验证目标）
- Docker Engine 23+，带 `docker compose` 插件
- 宿主机 Node.js 18/20（构建前端；已有产物可 `SKIP_FRONTEND=1`）
- 能访问 Docker Hub 和 Maven 镜像源
- 宿主机 80 / 8888 / 8889 空闲
- 建议 2GB+ 内存（Maven 构建、MySQL、JVM 同时跑；构建阶段已限制 768m 堆）

### 2.2 一键部署

```bash
cd /path/to/my-blog
bash deploy/setup-java-docker.sh
```

脚本依次做：

1. 构建前台/后台（`deploy/build-frontends.sh`，需要宿主机有 Node 18/20）；
2. 首次运行时从 `deploy/java/.env.example` 生成 `deploy/java/.env`，并生成随机
   MySQL 密码、`JWT_SECRET`、超管密码（文件权限 600）；
3. 校验 compose 配置，`docker compose up -d --build`；
4. 轮询 `http://127.0.0.1:8888/config`，就绪后打印访问地址和超管密码。

> Nginx 镜像固定为 `nginx:1.31.2-alpine`。**1.31.3+ 在 CentOS 7 + Docker CE 26 上写
> `/run/nginx.pid` 会 `EPERM`，容器进入重启循环**，见官方 issue
> [#1059](https://github.com/nginx/docker-nginx/issues/1059)。不要改回 `nginx:alpine` 浮标签。

访问：

- 前台 `http://<IP>/`
- 后台 `http://<IP>/admin/`（账号 `admin`）
- Swagger `http://<IP>:8888/swagger`
- WebSocket `ws://<IP>:8889/`

### 2.3 手动控制

```bash
cd /path/to/my-blog
bash deploy/build-frontends.sh        # 必须先有 ui/{blog,admin}/dist
cd deploy/java
cp .env.example .env                  # 首次；改密码和 OSS 配置
docker compose --env-file .env up -d --build
docker compose --env-file .env ps
docker compose --env-file .env logs -f blog-java-server
# 只起后端 + 数据库（不需要 nginx/前端时）
docker compose --env-file .env up -d db blog-java-server
docker compose --env-file .env down          # 保留数据卷
docker compose --env-file .env down -v       # 连数据卷一起删（会清库）
```

更新代码：

```bash
cd /path/to/my-blog
bash deploy/setup-java-docker.sh             # 会重新构建并滚动启动
```

只想重启、不重新构建：

```bash
SKIP_FRONTEND=1 SKIP_BUILD=1 bash deploy/setup-java-docker.sh
```

### 2.4 可选：网易云音乐接口

默认不启动。需要音乐页时：

```bash
cd deploy/java
docker compose --env-file .env --profile music up -d
```

Nginx 的 `/wapi/` 用 Docker 内置 DNS 延迟解析，没起音乐容器时网站仍正常，只是音乐页 502。

### 2.5 数据库

- MySQL 8.0 容器，库名默认 `online_blog`，应用账号默认 `blog`。
- **Docker 自带 MySQL 容器，和宿主机已有的 MySQL 不是同一个库**。要复用旧数据，
  先 `mysqldump` 旧库，再导入容器（或改用非 Docker 手工部署连宿主机 MySQL）。
- 首次创建数据卷时自动导入 `blog-java-server/sql/online_blog.sql`（只挂主库结构，
  不会导入 `seed-demo.sql`）。
- 该 SQL 含 `DROP TABLE IF EXISTS`，**只在空数据卷执行一次**；后续重启不会重复导入。
- **改了 `MYSQL_DB` / `MYSQL_USER` / `MYSQL_PASSWORD` 之后**，已有的 `db_data` 不会自动跟着改，
  应用会连不上或指向空库。要么进容器手动改账号/建库，要么 `docker compose down -v` 重建数据卷（会清库）。
- 备份：

```bash
cd deploy/java
docker compose --env-file .env exec -T db \
  sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" online_blog' \
  > backup-$(date +%F).sql
```

---

## 3. 方案 B：非 Docker 手工部署

### 3.1 一键脚本

```bash
cd /path/to/my-blog
bash deploy/setup-java-manual.sh
```

建议用普通用户执行（脚本内部自己调 `sudo`），不要 `sudo bash`，否则仓库里的 `target/` 会变成 root 所有。

脚本依次做：

1. apt 安装 JDK 17、Maven、Nginx、MySQL、Node 18（缺什么装什么）；
2. 建库/建应用账号（默认 `blog`）/ 导入 `online_blog.sql`（已有 `blog_article` 表则跳过，避免清库）；
3. 构建前端，构建 jar（在临时目录构建，自动剔除 `application-local.yml`）；
4. 安装后端到 `/opt/blog`（`blog-server.jar`、`upload/`），前端到 `/var/www/blog`（`blog/`、`admin/`）；
5. 生成 `/etc/blog-java/blog-java.env`（首次，随机密码/JWT/超管密码）；
6. 安装 systemd 服务并设为开机自启；
7. 覆盖 `/etc/nginx/conf.d/blog.conf`（会备份旧文件），停用 `/etc/nginx/sites-enabled/default`；
8. 启动服务并轮询 `/config` 自检。

常用开关：

```bash
SKIP_DEPS=1      bash deploy/setup-java-manual.sh   # 依赖已装好
SKIP_FRONTEND=1  bash deploy/setup-java-manual.sh   # 复用 ui
SKIP_BUILD=1     bash deploy/setup-java-manual.sh   # 复用 target 里的 jar
SKIP_DB=1        bash deploy/setup-java-manual.sh   # MySQL 已手工准备好（配合 APP_DB_USER/APP_DB_PASSWORD 或已有 ENV_FILE）
SKIP_NGINX=1     bash deploy/setup-java-manual.sh   # 保留现有 nginx 配置
SKIP_ADMIN=1     bash deploy/setup-java-manual.sh   # 只构建前台（build-frontends.sh 支持）
RUN_TESTS=1      bash deploy/setup-java-manual.sh   # 构建时跑单元测试（需要测试所需的本机配置）
INSTALL_DIR=/srv/blog WEB_DIR=/srv/www/blog bash deploy/setup-java-manual.sh
RUN_USER=www-data bash deploy/setup-java-manual.sh      # 改 systemd 运行用户（默认 www-data）
```

MySQL root 是密码登录（不是 socket 认证）时，传 root 密码：

```bash
MYSQL_ROOT_PASSWORD='你的root密码' bash deploy/setup-java-manual.sh
```

首次部署还想直接指定应用数据库账号 / 密码 / 超管密码：

```bash
APP_DB_NAME=online_blog APP_DB_USER=blog APP_DB_PASSWORD='强密码' \
ADMIN_PASSWORD='后台超管密码' JWT_SECRET='随机长串' \
bash deploy/setup-java-manual.sh
```

### 3.2 不用脚本时的手工步骤

以下以 `/opt/blog`（后端）和 `/var/www/blog`（前端）为例，Ubuntu 22.04。

#### 1）依赖

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk-headless maven nginx mysql-server curl
# 前端构建还需要 Node 18/20
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs
sudo npm i -g pnpm@7
```

#### 2）数据库

```bash
sudo mysql <<'SQL'
CREATE DATABASE IF NOT EXISTS online_blog
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'blog'@'localhost' IDENTIFIED BY '换成强密码';
CREATE USER IF NOT EXISTS 'blog'@'127.0.0.1' IDENTIFIED BY '换成强密码';
GRANT ALL PRIVILEGES ON online_blog.* TO 'blog'@'localhost';
GRANT ALL PRIVILEGES ON online_blog.* TO 'blog'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL

# 空库才导入；已有数据不要执行（脚本里有 DROP TABLE）
sudo mysql online_blog < blog-java-server/sql/online_blog.sql
```

> 如果旧 Node 版是 Docker 部署，数据在它自己的 MySQL 容器/卷里，不在宿主机 MySQL。
> 先 `docker compose exec db mysqldump -uroot -p online_blog > old.sql`，再导入宿主机：
> `sudo mysql online_blog < old.sql`。

#### 3）构建前端

```bash
bash deploy/build-frontends.sh
# 产物在 ui/blog/dist 与 ui/admin/dist
```

#### 4）构建 jar

```bash
cd blog-java-server
mvn -Dmaven.test.skip=true package
# 产物 target/blog-server-1.0.0.jar
```

> `src/main/resources/application-local.yml` 是本机开发配置（可能含数据库密码/OSS 密钥），
> 不要带进部署包。手工构建时先把它移走，或直接用 `setup-java-manual.sh`（会在临时目录构建并剔除它）。

#### 5）安装文件

```bash
sudo mkdir -p /opt/blog /var/www/blog
sudo install -m 644 blog-java-server/target/blog-server-1.0.0.jar /opt/blog/blog-server.jar
sudo rm -rf /var/www/blog/blog /var/www/blog/admin
sudo cp -r ui/blog/dist  /var/www/blog/blog
sudo cp -r ui/admin/dist /var/www/blog/admin
sudo mkdir -p /opt/blog/upload/local /opt/blog/upload/online
sudo chown -R www-data:www-data /opt/blog/upload
```

#### 6）环境变量

```bash
sudo mkdir -p /etc/blog-java
sudo cp deploy/manual/java/blog-java.env.example /etc/blog-java/blog-java.env
sudo chmod 600 /etc/blog-java/blog-java.env
sudo vi /etc/blog-java/blog-java.env
# 至少改：MYSQL_PASSWORD / SPRING_DATASOURCE_PASSWORD、ADMIN_PASSWORD、JWT_SECRET、OSS_*
```

Spring Boot **不读** Node 版的 `.env`，参数全部走 systemd 的 `EnvironmentFile`。
`SPRING_DATASOURCE_*` 的优先级高于 `application-local.yml`，即使 jar 里误带了本地配置也会连到正确库。

#### 7）systemd

```bash
# 如果改了 INSTALL_DIR / ENV_FILE，先改 unit 里的 WorkingDirectory、ExecStart、EnvironmentFile
sudo cp deploy/manual/java/blog-java-server.service /etc/systemd/system/blog-java-server.service
sudo systemctl daemon-reload
sudo systemctl enable --now blog-java-server
sudo journalctl -u blog-java-server -f
```

#### 8）Nginx

```bash
# 改过 WEB_DIR / APP_PORT / WS_PORT 时，先按实际值改这个模板里的路径和端口
sudo cp deploy/manual/java/nginx-blog-java.conf /etc/nginx/conf.d/blog.conf
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

#### 9）验证

```bash
curl -s http://127.0.0.1:8888/                  # 欢迎语
curl -s http://127.0.0.1:8888/config | head -c 300
curl -s http://127.0.0.1:8888/statistic
curl -sI http://127.0.0.1/                      # 前台
curl -sI http://127.0.0.1/admin/                # 后台
```

> 音乐页需要本机 3000 端口有网易云接口（`docker run -d --name netease -p 3000:3000 binaryify/netease_cloud_music_api`）；
> 没有也不影响其他页面，只是 `/wapi/` 返回 502。

---

## 4. 配置项

### 4.1 Docker（`deploy/java/.env`）

| 变量 | 默认 | 说明 |
|---|---|---|
| `HTTP_PORT` | 80 | 宿主机网站入口端口 |
| `APP_PORT` / `WS_PORT` | 8888 / 8889 | 宿主机后端/WS 端口；容器内固定 8888/8889 |
| `MYSQL_ROOT_PASSWORD` | 随机 | MySQL root 密码，仅容器初始化用 |
| `MYSQL_DB` | online_blog | 库名 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | blog / 随机 | 应用连接账号（不能是 root） |
| `ADMIN_PASSWORD` | 随机 | 内置超管 `admin` 的密码 |
| `JWT_SECRET` | 随机 | JWT 密钥；与 Node 版共用同一值才能互认 token |
| `UPLOADTYPE` | oss | `oss` / `local` / `online`（Java 未实现 qiniu、minio） |
| `OSS_*` | 空 | 阿里云 OSS 配置，见 `blog-java-server/README.md` |
| `JAVA_OPTS` | MaxRAMPercentage=75 | JVM 参数 |
| `MAVEN_MIRROR` | 阿里云 | 构建镜像时的 Maven 镜像 |

### 4.2 手工部署（`/etc/blog-java/blog-java.env`）

比 Docker 多出的关键项：

| 变量 | 说明 |
|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` | 默认 `127.0.0.1:3306` |
| `SPRING_DATASOURCE_URL` | Spring 标准变量，强制覆盖配置文件的 datasource URL |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | 同上，覆盖用户名/密码 |
| `JAVA_OPTS` | 默认 `-Xms256m -Xmx512m`，小内存虚拟机友好 |

环境文件是 systemd `EnvironmentFile` 格式：`KEY=VALUE` 一行一个，**不要写 `export`**；
值含空格/特殊字符时用双引号包起来。改完执行 `sudo systemctl restart blog-java-server`。

---

## 5. 注意事项与已知坑

1. **`application-local.yml` 不能进部署包**
   它已被 `.gitignore` 忽略，但本地存在。Docker 镜像构建会删掉它；手工脚本会在临时目录构建时剔除。
   即使误打进去，`SPRING_DATASOURCE_*` 环境变量也会覆盖里面的数据库地址/账号/密码。

2. **Spring 不读 Node 的 `.env`**
   原 `blog-server/.env` 里有行内注释，Spring 的 properties 解析会出错。Java 版参数全部走环境变量。

3. **上传模式不同**
   Java 版只实现 `oss` / `local` / `online`；`UPLOADTYPE=qiniu|minio` 会返回 `100006`。
   默认 `oss`，没有 OSS 密钥时上传会失败，可临时改 `local` 只做联调（返回的 URL 是 `http://127.0.0.1:8888/local/...`，
   只适合在本机浏览器打开）。

4. **本地上传目录按进程工作目录解析**
   Docker 下是 `/app/upload`（已挂 `java_upload` 卷）；手工部署是 `/opt/blog/upload`（systemd 的 `WorkingDirectory=/opt/blog`）。
   从 Node 版迁移时，旧文件在 `blog-server/src/upload/local`（或你当时的 `UPLOADTYPE=online` 目录），
   需要手动拷到 `/opt/blog/upload/local`（或 Docker 卷）才能继续用旧 URL 访问。

5. **JWT 兼容**
   Java 用手写 HS256，可与 Node 的 `jsonwebtoken` 互认；前提是两边 `JWT_SECRET` 相同。
   部署脚本会生成随机值，从 Node 切过来时要么沿用旧值，要么让用户重新登录。

6. **数据库不要重复导入**
   `online_blog.sql` 带 `DROP TABLE IF EXISTS`。手工脚本只在 `blog_article` 不存在时导入；
   手工执行时务必先确认库是空的。

7. **聊天 WebSocket 的前端地址写死在源码里**
   `blog-v3/src/components/ChatRoom/index.vue` 当前是 `ws://localhost:8889/`。远程访问时聊天室连不上，
   需要改成 `ws://<域名>/ws/`（HTTPS 用 `wss://`）后重新构建前端。Nginx 的 `/ws/` 已经配好转发。

8. **旧 Node 部署（如果机器上还有）**
   本仓库已移除 Node 后端。如果机器上还保留旧的 Node 部署，切换前先停掉：
   ```bash
   # 旧 Docker 栈（在当时的目录）
   docker compose down
   # 旧 systemd 服务
   sudo systemctl stop blog-server
   ```

9. **管理后台必须挂在 `/admin/`**
   后台是 hash 路由 + 相对路径，nginx 用 `alias` + `try_files` 回退到 `/admin/index.html`。

---

## 6. 排查

| 现象 | 排查 |
|---|---|
| `docker compose up` 报端口占用 | 先停掉旧容器/旧服务：`ss -lntp \| grep -E '80\|8888\|8889'` |
| 后端容器 unhealthy | `docker compose logs blog-java-server`；多半是 MySQL 未初始化完或密码不一致 |
| `/config` 返回 500 | 数据库连不上或表没导入；`docker compose exec db mysql ...` 查表 |
| 手工部署服务起不来 | `sudo journalctl -u blog-java-server -n 80`；`sudo tail -n 80 /var/log/blog-java/err.log` |
| 页面 502 | nginx 起来了但后端没起；先 `curl 127.0.0.1:8888/config` |
| 页面白屏/404 静态资源 | `ui/blog/dist/index.html` 是否为空；Docker nginx root 是 `.../blog/dist`，手工是 `/var/www/blog/blog` |
| `/admin/` 404 | 没有构建 admin；`bash deploy/build-frontends.sh`（需要 pnpm 7） |
| 上传返回 100006 | `UPLOADTYPE` 与 OSS/本地配置不匹配；Java 不支持 qiniu/minio |
| nginx 反复重启，日志 `pwrite() "/run/nginx.pid" failed (1: Operation not permitted)` | 镜像缺陷：固定 `nginx:1.31.2-alpine`；1.31.3+ 在 CentOS 7 + Docker CE 26 有此问题（[issue #1059](https://github.com/nginx/docker-nginx/issues/1059)） |
| Maven 构建 OOM | `MAVEN_OPTS=-Xmx1536m`，或给虚拟机加内存；Docker 构建已限制 768m |
| 后台登录 token 老失效 | 切换后 `JWT_SECRET` 变了；要和旧环境互认就沿用旧值 |
| 访问几百次后整站 429 | 限流按 `remoteAddress` 计数；经过 nginx 时所有请求看起来都来自代理 IP。生产可加 `SERVER_FORWARD_HEADERS_STRATEGY=native` 让 Tomcat 读 `X-Forwarded-For`（会偏离现网行为，评估后再用） |
| 脚本报 `bash\r` / `$'\r': command not found` | Windows 转成了 CRLF：`sed -i 's/\r$//' deploy/*.sh`；仓库 `.gitattributes` 已默认 LF |

---

## 7. 验证状态

- ✅ Java 后端本身已在本机完成 `mvn package` 与 103/103 接口实现验证（见 `blog-java-server/README.md`）。
- ⏳ `deploy/setup-java-docker.sh`、`deploy/setup-java-manual.sh` 及 compose/nginx 配置**尚未在虚拟机实测**，
  目前只做了 `bash -n` 语法检查；等本地虚拟机验证后再补结果。
- ⏳ Docker 构建链（Maven 镜像、MySQL 8.0 初始化、nginx 反代、可选音乐服务）同上。

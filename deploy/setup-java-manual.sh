#!/usr/bin/env bash
# Java 后端（Spring Boot）手工部署（非 Docker）一键脚本
#
# 做的事：装依赖 -> 建库建用户并导入表结构 -> 构建前端 -> 构建 jar -> 安装到 /opt/blog + /var/www/blog
#        -> 生成 /etc/blog-java/blog-java.env -> 装 systemd 服务 -> 配置 nginx -> 启动自检
#
# 适用：Ubuntu 22.04 / Debian 12。建议用普通用户执行（脚本内部用 sudo），
#       不要用 `sudo bash setup-java-manual.sh`，否则仓库里的 target/ 会变成 root 所有。
#
# 常用开关（环境变量）：
#   SKIP_DEPS=1       跳过 apt 安装（机器上已有 JDK17 / Maven / Nginx / MySQL / Node）
#   SKIP_FRONTEND=1   复用 ui/ 下已有的前端产物
#   SKIP_BUILD=1      复用 blog-java-server/target 下已有的 jar
#   SKIP_DB=1         不自动建库/导入，只校验/跳过（MySQL 由你手工准备；请同时给
#                     APP_DB_USER / APP_DB_PASSWORD，或先放好 ENV_FILE，否则随机密码连不上旧库）
#   SKIP_NGINX=1      不覆盖 nginx 配置（自己已有反代时用）
#   RUN_TESTS=1       构建时跑单元测试（默认跳过，部署更快）
#   INSTALL_DIR / WEB_DIR / RUN_USER  覆盖安装路径和 systemd 运行用户
#
# 首次部署可覆盖：APP_DB_NAME / APP_DB_USER / APP_DB_PASSWORD / JWT_SECRET / ADMIN_PASSWORD
#                / UPLOADTYPE / OSS_*；已有的 /etc/blog-java/blog-java.env 优先，不会被覆盖。
# MySQL 管理员密码（root 是密码登录而不是 auth_socket 时）：MYSQL_ROOT_PASSWORD=...
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_DIR="$ROOT/blog-java-server"
INSTALL_DIR="${INSTALL_DIR:-/opt/blog}"
# 前端放 /var/www 下：Ubuntu 的 nginx AppArmor 配置默认允许 /var/www，/opt 可能 403
WEB_DIR="${WEB_DIR:-/var/www/blog}"
ENV_DIR="${ENV_DIR:-/etc/blog-java}"
ENV_FILE="${ENV_FILE:-$ENV_DIR/blog-java.env}"
SERVICE_NAME="blog-java-server"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
NGINX_CONF="/etc/nginx/conf.d/blog.conf"
RUN_USER="${RUN_USER:-www-data}"
RUN_GROUP=""
MYSQL_ADMIN_MODE=""
TMP_DIR=""
MAVEN_SETTINGS_TMP=""

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[提示] %s\033[0m\n' "$*"; }
die() { printf '\033[1;31m[错误] %s\033[0m\n' "$*" >&2; exit 1; }

cleanup() {
  [ -n "$TMP_DIR" ] && rm -rf "$TMP_DIR"
  [ -n "$MAVEN_SETTINGS_TMP" ] && rm -f "$MAVEN_SETTINGS_TMP"
  return 0
}
trap cleanup EXIT

if [ "$(id -u)" -eq 0 ]; then
  as_root() { "$@"; }
  warn "当前是 root 直接执行；建议改用普通用户执行，脚本会自己调 sudo"
else
  command -v sudo >/dev/null 2>&1 || die "需要 sudo（或直接用 root 执行）"
  as_root() { sudo "$@"; }
fi

command -v apt-get >/dev/null 2>&1 || die "本脚本只支持 Debian/Ubuntu（需要 apt-get）"
[ -f "$JAVA_DIR/pom.xml" ] || die "找不到 $JAVA_DIR/pom.xml，请在仓库目录内执行"
[ -f "$ROOT/deploy/manual/java/blog-java-server.service" ] || die "缺少 deploy/manual/java/blog-java-server.service"
[ -f "$ROOT/deploy/manual/java/nginx-blog-java.conf" ] || die "缺少 deploy/manual/java/nginx-blog-java.conf"
case "$INSTALL_DIR" in
  /*) ;;
  *) die "INSTALL_DIR 必须是绝对路径：$INSTALL_DIR" ;;
esac
[ "$INSTALL_DIR" != "/" ] || die "INSTALL_DIR 不能是 /"
case "$INSTALL_DIR" in
  *[[:space:]]*) die "INSTALL_DIR 不能包含空格：$INSTALL_DIR" ;;
esac
case "$WEB_DIR" in
  /*) ;;
  *) die "WEB_DIR 必须是绝对路径：$WEB_DIR" ;;
esac
[ "$WEB_DIR" != "/" ] || die "WEB_DIR 不能是 /"
case "$WEB_DIR" in
  *[[:space:]]*) die "WEB_DIR 不能包含空格：$WEB_DIR" ;;
esac
case "$ENV_FILE" in
  /*) ;;
  *) die "ENV_FILE 必须是绝对路径：$ENV_FILE" ;;
esac

rand_hex() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "$1"
  else
    od -An -N "$1" -tx1 /dev/urandom | tr -d ' \n'
  fi
}

env_get() {
  local value
  value="$(sed -n "s/^[[:space:]]*$1=//p" "$ENV_FILE" 2>/dev/null | tail -n 1 | tr -d '\r')"
  value="${value%\"}"; value="${value#\"}"
  value="${value%\'}"; value="${value#\'}"
  printf '%s' "$value"
}

# ---------- 1. 依赖 ----------
if [ "${SKIP_DEPS:-0}" != "1" ]; then
  say "更新 apt 索引"
  as_root env DEBIAN_FRONTEND=noninteractive apt-get update -y

  PKGS=()
  command -v java >/dev/null 2>&1 || PKGS+=(openjdk-17-jdk-headless)
  if ! command -v mvn >/dev/null 2>&1 && [ "${SKIP_BUILD:-0}" != "1" ]; then
    PKGS+=(maven)
  fi
  if ! command -v nginx >/dev/null 2>&1 && [ ! -x /usr/sbin/nginx ] && [ "${SKIP_NGINX:-0}" != "1" ]; then
    PKGS+=(nginx)
  fi
  if ! command -v curl >/dev/null 2>&1; then
    PKGS+=(curl)
  fi
  if [ "${SKIP_DB:-0}" != "1" ] && ! command -v mysql >/dev/null 2>&1; then
    if apt-cache show mysql-server >/dev/null 2>&1; then
      PKGS+=(mysql-server)
    else
      PKGS+=(default-mysql-server)
    fi
  fi

  if [ "${#PKGS[@]}" -gt 0 ]; then
    say "安装依赖：${PKGS[*]}"
    as_root env DEBIAN_FRONTEND=noninteractive apt-get install -y "${PKGS[@]}"
  else
    say "系统依赖已齐全"
  fi

  if [ "${SKIP_DB:-0}" != "1" ]; then
    as_root systemctl enable --now mysql >/dev/null 2>&1 \
      || as_root systemctl enable --now mariadb >/dev/null 2>&1 \
      || warn "mysql/mariadb 服务未能自动启动，请手工确认"
  fi

  if [ "${SKIP_FRONTEND:-0}" != "1" ] && ! command -v node >/dev/null 2>&1; then
    say "安装 Node.js 18（构建前端需要）"
    curl -fsSL https://deb.nodesource.com/setup_18.x -o /tmp/nodesource_setup.sh \
      || die "下载 NodeSource 安装脚本失败；可改 SKIP_FRONTEND=1 用现成产物"
    as_root bash /tmp/nodesource_setup.sh
    as_root env DEBIAN_FRONTEND=noninteractive apt-get install -y nodejs
  fi
  if [ "${SKIP_FRONTEND:-0}" != "1" ] && command -v npm >/dev/null 2>&1 && ! command -v pnpm >/dev/null 2>&1; then
    as_root npm install -g pnpm@7 >/dev/null 2>&1 || warn "pnpm 安装失败，后台管理端可能被跳过（可手动 npm i -g pnpm@7）"
  fi
fi

command -v java >/dev/null 2>&1 || die "未找到 java，请安装 openjdk-17-jdk-headless（或去掉 SKIP_DEPS=1）"
JAVA_BIN="$(command -v java)"
JAVA_VER="$(java -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
JAVA_MAJOR="${JAVA_VER%%.*}"
[ "$JAVA_MAJOR" = "1" ] && JAVA_MAJOR="$(printf '%s' "$JAVA_VER" | cut -d. -f2)"
JAVA_MAJOR="${JAVA_MAJOR%%[!0-9]*}"
[ "${JAVA_MAJOR:-0}" -ge 17 ] || die "需要 JDK 17+，当前是 ${JAVA_VER:-未知版本}；请安装 openjdk-17-jdk-headless 并切换到 17"
# 让 Maven 用和 java 命令同一个 JDK，避免环境里残留旧 JAVA_HOME
JAVA_HOME_REAL="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")"
[ -x "$JAVA_HOME_REAL/bin/java" ] && export JAVA_HOME="$JAVA_HOME_REAL"
command -v curl >/dev/null 2>&1 || die "未找到 curl，请安装（或去掉 SKIP_DEPS=1 重跑）"

id "$RUN_USER" >/dev/null 2>&1 || die "系统用户 $RUN_USER 不存在（安装 nginx 后一般会有 www-data）"
RUN_GROUP="$(id -gn "$RUN_USER")"

# ---------- 2. 前端 ----------
if [ "${SKIP_FRONTEND:-0}" = "1" ]; then
  [ -f "$ROOT/ui/blog/dist/index.html" ] || \
    die "SKIP_FRONTEND=1 但 ui/blog/dist/index.html 不存在"
  say "跳前端构建（SKIP_FRONTEND=1）"
else
  say "构建前端（首次约 3-5 分钟）"
  bash "$ROOT/deploy/build-frontends.sh"
fi

# ---------- 3. 构建 jar ----------
if [ "${SKIP_BUILD:-0}" = "1" ]; then
  JAR_PATH="$(find "$JAVA_DIR/target" -maxdepth 1 -type f -name 'blog-server-*.jar' \
    ! -name '*-sources.jar' ! -name '*-javadoc.jar' 2>/dev/null | sort | head -n 1 || true)"
  [ -n "$JAR_PATH" ] || die "SKIP_BUILD=1 但 $JAVA_DIR/target 下没有 blog-server-*.jar"
  say "跳构建（SKIP_BUILD=1），使用 $JAR_PATH"
else
  command -v mvn >/dev/null 2>&1 || die "未找到 mvn，请安装 maven 或设置 SKIP_BUILD=1 复用已有 jar"

  MAVEN_SETTINGS="$ROOT/deploy/java/mvn-settings.xml"
  if [ -n "${MAVEN_MIRROR:-}" ] && [ -f "$MAVEN_SETTINGS" ]; then
    MAVEN_SETTINGS_TMP="$(mktemp)"
    sed "s|https://maven.aliyun.com/repository/public|${MAVEN_MIRROR}|" \
      "$MAVEN_SETTINGS" > "$MAVEN_SETTINGS_TMP"
    MAVEN_SETTINGS="$MAVEN_SETTINGS_TMP"
  fi

  TMP_DIR="$(mktemp -d)"
  BUILD_SRC="$TMP_DIR/src"
  say "复制源码到临时目录构建（剔除 target 和 application-local.yml）"
  mkdir -p "$BUILD_SRC"
  cp -a "$JAVA_DIR/pom.xml" "$BUILD_SRC/pom.xml"
  cp -a "$JAVA_DIR/src" "$BUILD_SRC/src"
  rm -f "$BUILD_SRC/src/main/resources/application-local.yml"

  say "Maven 打包（首次会下载依赖）"
  MAVEN_ARGS=(-B)
  [ -f "$MAVEN_SETTINGS" ] && MAVEN_ARGS+=(-s "$MAVEN_SETTINGS")
  if [ "${RUN_TESTS:-0}" = "1" ]; then
    ( cd "$BUILD_SRC" && mvn "${MAVEN_ARGS[@]}" package )
  else
    ( cd "$BUILD_SRC" && mvn "${MAVEN_ARGS[@]}" -Dmaven.test.skip=true package )
  fi

  JAR_SRC="$(find "$BUILD_SRC/target" -maxdepth 1 -type f -name 'blog-server-*.jar' \
    ! -name '*-sources.jar' ! -name '*-javadoc.jar' 2>/dev/null | sort | head -n 1 || true)"
  [ -n "$JAR_SRC" ] || die "Maven 构建结束但没有找到 target/blog-server-*.jar"

  mkdir -p "$JAVA_DIR/target"
  cp -f "$JAR_SRC" "$JAVA_DIR/target/$(basename "$JAR_SRC")"
  JAR_PATH="$JAVA_DIR/target/$(basename "$JAR_SRC")"
  say "jar 已生成：$JAR_PATH"
fi

# ---------- 4. 数据库 ----------
APP_PORT="${APP_PORT:-8888}"
WS_PORT="${WS_PORT:-8889}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
ENV_CREATED=0
DB_NAME=""
DB_USER=""
DB_PASS=""
JWT_PW=""
ADMIN_PW=""

if [ -f "$ENV_FILE" ]; then
  APP_PORT="$(env_get APP_PORT)"; APP_PORT="${APP_PORT:-8888}"
  WS_PORT="$(env_get CHAT_WS_PORT)"; WS_PORT="${WS_PORT:-8889}"
  MYSQL_PORT="$(env_get MYSQL_PORT)"; MYSQL_PORT="${MYSQL_PORT:-3306}"
  DB_NAME="$(env_get MYSQL_DB)"; DB_NAME="${DB_NAME:-online_blog}"
  DB_USER="$(env_get MYSQL_USER)"; DB_USER="${DB_USER:-blog}"
  DB_PASS="$(env_get MYSQL_PASSWORD)"
  JWT_PW="$(env_get JWT_SECRET)"
  ADMIN_PW="$(env_get ADMIN_PASSWORD)"
  say "复用已有 $ENV_FILE"
else
  DB_NAME="${APP_DB_NAME:-online_blog}"
  DB_USER="${APP_DB_USER:-blog}"
  DB_PASS="${APP_DB_PASSWORD:-$(rand_hex 24)}"
  JWT_PW="${JWT_SECRET:-$(rand_hex 48)}"
  ADMIN_PW="${ADMIN_PASSWORD:-Admin-$(rand_hex 10)}"
fi

case "$DB_NAME" in
  ''|*[!A-Za-z0-9_]*) die "MYSQL_DB 只能包含字母、数字和下划线：$DB_NAME" ;;
esac
case "$DB_USER" in
  ''|*[!A-Za-z0-9_]*) die "MYSQL_USER 只能包含字母、数字和下划线：$DB_USER" ;;
esac
for port in "$APP_PORT" "$WS_PORT" "$MYSQL_PORT"; do
  case "$port" in
    ''|*[!0-9]*) die "端口必须是数字：$port" ;;
  esac
  [ "$port" -ge 1 ] && [ "$port" -le 65535 ] || die "端口超出 1-65535：$port"
done
if [ -f "$ENV_FILE" ]; then
  case "$DB_PASS" in
    ''|change-me*) die "$ENV_FILE 里的 MYSQL_PASSWORD 还是空/示例值，请改掉，或删掉该文件让脚本重新生成" ;;
  esac
fi
for secret in "$DB_PASS" "$ADMIN_PW" "$JWT_PW"; do
  case "$secret" in
    *[[:space:]]*) die "数据库密码 / 超管密码 / JWT 不能包含空白字符（systemd EnvironmentFile 会截断）" ;;
  esac
done

# 密码为空时不要传 -p，否则 mysql 会进入交互式密码提示
MYSQL_APP_ARGS=(-h 127.0.0.1 -P "$MYSQL_PORT" -u"$DB_USER")
[ -n "$DB_PASS" ] && MYSQL_APP_ARGS+=("-p$DB_PASS")

if [ "${SKIP_DB:-0}" = "1" ]; then
  warn "SKIP_DB=1，跳过建库/导入；请确认 $ENV_FILE 里的数据库配置可用"
else
  command -v mysql >/dev/null 2>&1 || die "未找到 mysql 客户端；装好 MySQL 或设置 SKIP_DB=1"

  if as_root mysql -N -e 'SELECT 1' >/dev/null 2>&1; then
    MYSQL_ADMIN_MODE="socket"
  elif [ -n "${MYSQL_ROOT_PASSWORD:-}" ] && \
       mysql -h 127.0.0.1 -P "$MYSQL_PORT" -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e 'SELECT 1' >/dev/null 2>&1; then
    MYSQL_ADMIN_MODE="tcp"
  fi

  run_mysql_admin() {
    case "$MYSQL_ADMIN_MODE" in
      socket) as_root mysql "$@" ;;
      tcp) mysql -h 127.0.0.1 -P "$MYSQL_PORT" -uroot -p"$MYSQL_ROOT_PASSWORD" "$@" ;;
      *) return 1 ;;
    esac
  }

  if mysql "${MYSQL_APP_ARGS[@]}" -N \
       -e "SELECT 1 FROM \`$DB_NAME\`.blog_article LIMIT 1" >/dev/null 2>&1; then
    say "数据库 $DB_NAME 已就绪（账号 $DB_USER），跳过初始化"
  else
    [ -n "$MYSQL_ADMIN_MODE" ] || die "无法用 root 登录 MySQL，也连不上 $DB_USER。\
请先执行 sudo mysql 初始化 root 密码，或用 MYSQL_ROOT_PASSWORD=... 重跑；已有库可设 SKIP_DB=1"

    say "初始化数据库 $DB_NAME 与账号 $DB_USER"
    DB_PASS_SQL="${DB_PASS//\'/\'\'}"
    run_mysql_admin -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    if [ "$DB_USER" != "root" ]; then
      run_mysql_admin -e "CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS_SQL';"
      run_mysql_admin -e "ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS_SQL';"
      run_mysql_admin -e "CREATE USER IF NOT EXISTS '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASS_SQL';"
      run_mysql_admin -e "ALTER USER '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASS_SQL';"
      run_mysql_admin -e "GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'localhost';"
      run_mysql_admin -e "GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'127.0.0.1';"
      run_mysql_admin -e "FLUSH PRIVILEGES;"
    fi

    TABLE_COUNT="$(run_mysql_admin -N -e \
      "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name='blog_article'" \
      | tr -d '[:space:]')"
    if [ "${TABLE_COUNT:-0}" -eq 0 ]; then
      say "导入 blog-java-server/sql/online_blog.sql"
      run_mysql_admin "$DB_NAME" < "$JAVA_DIR/sql/online_blog.sql"
    else
      warn "数据库里已有 blog_article 表，跳过导入（避免 DROP TABLE 清库）"
    fi

    mysql "${MYSQL_APP_ARGS[@]}" -N \
      -e "SELECT 1 FROM \`$DB_NAME\`.blog_article LIMIT 1" >/dev/null 2>&1 \
      || die "已建库建用户，但 $DB_USER 仍无法读取 $DB_NAME.blog_article，请检查 MySQL 授权"
  fi
fi

# ---------- 5. 安装文件 ----------
[ -d "$ROOT/ui/blog/dist" ] || die "没有前端产物 ui/blog/dist，请先构建前端"
say "安装后端到 $INSTALL_DIR，前端到 $WEB_DIR"
as_root install -d -m 755 "$INSTALL_DIR" "$WEB_DIR"
as_root install -m 644 "$JAR_PATH" "$INSTALL_DIR/blog-server.jar"

as_root rm -rf "$WEB_DIR/blog" "$WEB_DIR/admin"
as_root cp -r "$ROOT/ui/blog/dist" "$WEB_DIR/blog"
if [ -d "$ROOT/ui/admin/dist" ]; then
  as_root cp -r "$ROOT/ui/admin/dist" "$WEB_DIR/admin"
else
  warn "没有管理端产物（deploy/build-frontends.sh 跳过了 admin），/admin/ 会 404"
fi

as_root install -d -m 755 -o "$RUN_USER" -g "$RUN_GROUP" \
  "$INSTALL_DIR/upload" "$INSTALL_DIR/upload/local" "$INSTALL_DIR/upload/online"
as_root chown -R "$RUN_USER:$RUN_GROUP" "$INSTALL_DIR/upload"

# ---------- 6. 环境变量 ----------
if [ ! -f "$ENV_FILE" ]; then
  say "生成 $ENV_FILE"
  as_root install -d -m 750 "$(dirname "$ENV_FILE")"
  TMP_ENV="$(mktemp)"
  {
    printf '# 由 deploy/setup-java-manual.sh 生成：%s\n' "$(date '+%Y-%m-%d %H:%M:%S')"
    printf 'APP_PORT=%s\n' "$APP_PORT"
    printf 'CHAT_WS_PORT=%s\n' "$WS_PORT"
    printf '\nMYSQL_HOST=127.0.0.1\nMYSQL_PORT=%s\n' "$MYSQL_PORT"
    printf 'MYSQL_USER=%s\nMYSQL_PASSWORD=%s\nMYSQL_DB=%s\n' "$DB_USER" "$DB_PASS" "$DB_NAME"
    printf '\nSPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:%s/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&zeroDateTimeBehavior=convertToNull\n' "$MYSQL_PORT" "$DB_NAME"
    printf 'SPRING_DATASOURCE_USERNAME=%s\nSPRING_DATASOURCE_PASSWORD=%s\n' "$DB_USER" "$DB_PASS"
    printf '\nADMIN_PASSWORD=%s\nJWT_SECRET=%s\n' "$ADMIN_PW" "$JWT_PW"
    printf '\nUPLOADTYPE=%s\n' "${UPLOADTYPE:-oss}"
    printf 'OSS_ENDPOINT=%s\n' "${OSS_ENDPOINT:-}"
    printf 'OSS_BUCKET_NAME=%s\n' "${OSS_BUCKET_NAME:-}"
    printf 'ALIBABA_CLOUD_ACCESS_KEY_ID=%s\n' "${ALIBABA_CLOUD_ACCESS_KEY_ID:-}"
    printf 'ALIBABA_CLOUD_ACCESS_KEY_SECRET=%s\n' "${ALIBABA_CLOUD_ACCESS_KEY_SECRET:-}"
    printf 'OSS_DOMAIN=%s\n' "${OSS_DOMAIN:-}"
    printf 'OSS_DIR=%s\n' "${OSS_DIR:-images/}"
    printf 'OSS_REFERER=%s\n' "${OSS_REFERER:-}"
    printf 'OSS_PUBLIC_READ=%s\n' "${OSS_PUBLIC_READ:-true}"
    printf '\nJAVA_OPTS="%s"\n' "${JAVA_OPTS:--Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8}"
  } > "$TMP_ENV"
  as_root install -m 600 "$TMP_ENV" "$ENV_FILE"
  rm -f "$TMP_ENV"
  ENV_CREATED=1
else
  say "复用已有 $ENV_FILE（不覆盖）"
fi

case "$ADMIN_PW" in
  ''|change-me*) warn "ADMIN_PASSWORD 还是空/示例值，登录前请改 $ENV_FILE" ;;
esac
case "$JWT_PW" in
  ''|change-me*) warn "JWT_SECRET 还是空/示例值，生产环境务必换成随机长串" ;;
esac

# ---------- 7. systemd ----------
say "安装 systemd 服务 $SERVICE_NAME"
TMP_UNIT="$(mktemp)"
sed -e "s|/opt/blog|$INSTALL_DIR|g" \
    -e "s|/etc/blog-java/blog-java.env|$ENV_FILE|g" \
    -e "s|/usr/bin/java|$JAVA_BIN|g" \
    -e "s|^User=www-data$|User=$RUN_USER|" \
    -e "s|^Group=www-data$|Group=$RUN_GROUP|" \
    "$ROOT/deploy/manual/java/blog-java-server.service" > "$TMP_UNIT"
as_root install -m 644 "$TMP_UNIT" "$SERVICE_FILE"
rm -f "$TMP_UNIT"
as_root systemctl daemon-reload
as_root systemctl enable "$SERVICE_NAME" >/dev/null 2>&1 || true

# ---------- 8. nginx ----------
if [ "${SKIP_NGINX:-0}" = "1" ]; then
  warn "SKIP_NGINX=1，未修改 nginx 配置；前端产物在 $WEB_DIR"
else
  NGINX_BIN="$(command -v nginx 2>/dev/null || true)"
  [ -n "$NGINX_BIN" ] || NGINX_BIN="/usr/sbin/nginx"
  [ -x "$NGINX_BIN" ] || die "未找到 nginx；安装或设置 SKIP_NGINX=1"
  if [ -e /etc/nginx/sites-enabled/default ]; then
    as_root rm -f /etc/nginx/sites-enabled/default
    say "已停用 /etc/nginx/sites-enabled/default（否则 80 端口会被默认站点占用）"
  fi

  TMP_NGINX="$(mktemp)"
  # 先把端口换成占位符再替换，避免 APP_PORT / WS_PORT 互换时两条 sed 互相踩到
  sed -e "s|/var/www/blog|$WEB_DIR|g" \
      -e "s|127.0.0.1:8888|127.0.0.1:__JAVA_APP_PORT__|g" \
      -e "s|127.0.0.1:8889|127.0.0.1:__JAVA_WS_PORT__|g" \
      "$ROOT/deploy/manual/java/nginx-blog-java.conf" \
    | sed -e "s|__JAVA_APP_PORT__|$APP_PORT|g" \
          -e "s|__JAVA_WS_PORT__|$WS_PORT|g" > "$TMP_NGINX"
  NGINX_BACKUP=""
  if [ -f "$NGINX_CONF" ]; then
    NGINX_BACKUP="${NGINX_CONF}.bak.$(date +%s)"
    as_root cp -a "$NGINX_CONF" "$NGINX_BACKUP"
  fi
  as_root install -m 644 "$TMP_NGINX" "$NGINX_CONF"
  rm -f "$TMP_NGINX"

  if ! as_root "$NGINX_BIN" -t; then
    if [ -n "$NGINX_BACKUP" ]; then
      as_root cp -a "$NGINX_BACKUP" "$NGINX_CONF"
    else
      as_root rm -f "$NGINX_CONF"
    fi
    die "nginx 配置校验失败，已恢复原配置"
  fi
  as_root systemctl enable nginx >/dev/null 2>&1 || true
  as_root systemctl reload nginx 2>/dev/null \
    || as_root systemctl restart nginx \
    || die "nginx 启动失败，检查 80 端口是否被占用：ss -lntp | grep ':80 '"
fi

# ---------- 9. 启动与自检 ----------
say "启动 $SERVICE_NAME"
as_root systemctl restart "$SERVICE_NAME" || true

say "等待后端就绪"
READY=0
for i in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:${APP_PORT}/config" >/dev/null 2>&1; then
    echo "后端就绪（${i} 次探测，约 $((i * 2)) 秒）"
    READY=1
    break
  fi
  sleep 2
done

if [ "$READY" -ne 1 ]; then
  echo "----- journalctl -u $SERVICE_NAME -----" >&2
  as_root journalctl -u "$SERVICE_NAME" -n 80 --no-pager >&2 || true
  echo "----- /var/log/blog-java/err.log -----" >&2
  as_root tail -n 80 /var/log/blog-java/err.log >&2 || true
  echo "----- /var/log/blog-java/out.log -----" >&2
  as_root tail -n 80 /var/log/blog-java/out.log >&2 || true
  die "后端在 180 秒内没有就绪，按上面的日志排查"
fi

SITE_READY=0
for i in $(seq 1 10); do
  if curl -fsS "http://127.0.0.1/" >/dev/null 2>&1; then
    SITE_READY=1
    break
  fi
  sleep 1
done
[ "$SITE_READY" -eq 1 ] || warn "http://127.0.0.1/ 暂未返回 200，检查前端产物与 nginx 配置"

IP="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
[ -n "$IP" ] || IP="127.0.0.1"

printf '\n\033[1;32m==> 部署完成\033[0m\n'
cat <<EOF

  博客前台      http://$IP/
  后台管理      http://$IP/admin/     （账号 admin）
  后端直连      http://$IP:$APP_PORT/
  接口文档      http://$IP:$APP_PORT/swagger
  WebSocket     ws://$IP:$WS_PORT/

  超管密码      $(env_get ADMIN_PASSWORD)
  环境变量      $ENV_FILE
  后端目录      $INSTALL_DIR
  前端目录      $WEB_DIR
  查看状态      sudo systemctl status $SERVICE_NAME
  实时日志      sudo journalctl -u $SERVICE_NAME -f
  重启服务      sudo systemctl restart $SERVICE_NAME

EOF

if [ "$ENV_CREATED" -eq 1 ]; then
  cat <<EOF
首次部署生成了随机数据库密码 / JWT / 超管密码，都在 $ENV_FILE（权限 600）。
请登录后台后修改超管密码，并尽快配置 OSS 密钥（UPLOADTYPE=oss 时上传图片需要）。

EOF
fi

cat <<EOF
提示：
  - 如果机器上还留着旧 Node 后端/旧服务，先停掉（都会占 80/8888/8889）。
  - Java 版上传只支持 oss / local / online，不支持 qiniu / minio。
  - 前端聊天 WebSocket 目前写死在 blog-v3 源码里（ws://localhost:8889/），
    远程访问需要改成 ws://<域名>/ws/ 并重新构建前端。

EOF

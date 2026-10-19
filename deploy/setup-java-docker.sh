#!/usr/bin/env bash
# Java 后端（Spring Boot）一键 Docker 部署：构建前端 -> 生成 .env -> 构建镜像并起全栈
#
# 适用：全新 Ubuntu 22.04 / Debian 12 虚拟机，已安装 Docker Engine + compose 插件。
# 需要 root 或 docker 组权限；首次构建会拉取 maven / JDK / MySQL / Nginx 镜像。
#
# 常用开关（环境变量）：
#   SKIP_FRONTEND=1  复用 ui/ 下已有的前端产物
#   SKIP_BUILD=1     跳过镜像构建，直接用已有镜像起容器
#   WITH_MUSIC=1     同时启动网易云音乐接口容器
#   HTTP_PORT=8080   宿主机 80 被占用时换端口（也可直接改 deploy/java/.env）
#   APP_PORT / WS_PORT  宿主机后端 / WebSocket 端口（默认 8888 / 8889）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_DIR="$ROOT/deploy/java"
COMPOSE_FILE="$COMPOSE_DIR/docker-compose.yml"
ENVFILE="$COMPOSE_DIR/.env"
ENV_EXAMPLE="$COMPOSE_DIR/.env.example"
GENERATED_ENV=0

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[提示] %s\033[0m\n' "$*"; }
die() { printf '\033[1;31m[错误] %s\033[0m\n' "$*" >&2; exit 1; }

rand_hex() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "$1"
  else
    od -An -N "$1" -tx1 /dev/urandom | tr -d ' \n'
  fi
}

env_get() {
  local value
  value="$(sed -n "s/^[[:space:]]*$1=//p" "$ENVFILE" 2>/dev/null | tail -n 1 | tr -d '\r')"
  value="${value%\"}"; value="${value#\"}"
  value="${value%\'}"; value="${value#\'}"
  printf '%s' "$value"
}

require_env() {
  local value
  value="$(env_get "$1")"
  [ -n "$value" ] || die "$ENVFILE 缺少 $1，请参考 $ENV_EXAMPLE 补齐"
  case "$value" in
    change-me*) die "$ENVFILE 里的 $1 还是示例值，请改成自己的（或删掉 .env 让脚本重新生成）" ;;
  esac
}

# ---------- 0. 依赖检查 ----------
command -v docker >/dev/null 2>&1 || die "未找到 docker，请先安装 Docker Engine 与 compose 插件"
docker compose version >/dev/null 2>&1 || die "未找到 docker compose 插件（需要 Docker Compose v2）"
docker info >/dev/null 2>&1 || die "无法访问 Docker（daemon 没启动，或当前用户不在 docker 组）"
command -v curl >/dev/null 2>&1 || die "未找到 curl，请先安装：sudo apt-get install -y curl"
[ -f "$COMPOSE_FILE" ] || die "缺少 $COMPOSE_FILE"

chmod +x "$ROOT"/deploy/*.sh 2>/dev/null || true

# ---------- 1. 前端产物 ----------
if [ "${SKIP_FRONTEND:-0}" = "1" ]; then
  [ -f "$ROOT/ui/blog/dist/index.html" ] || \
    die "SKIP_FRONTEND=1 但 ui/blog/dist/index.html 不存在"
  say "跳前端构建（SKIP_FRONTEND=1），复用 ui/"
else
  say "构建前端（首次约 3-5 分钟）"
  bash "$ROOT/deploy/build-frontends.sh"
fi

# ---------- 2. .env ----------
if [ ! -f "$ENVFILE" ]; then
  [ -f "$ENV_EXAMPLE" ] || die "缺少 $ENV_EXAMPLE"
  cp "$ENV_EXAMPLE" "$ENVFILE"
  chmod 600 "$ENVFILE"

  ROOT_PW="$(rand_hex 24)"
  APP_PW="$(rand_hex 24)"
  JWT_PW="$(rand_hex 48)"
  ADMIN_PW="Admin-$(rand_hex 10)"

  sed -i "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=$ROOT_PW|" "$ENVFILE"
  sed -i "s|^MYSQL_PASSWORD=.*|MYSQL_PASSWORD=$APP_PW|" "$ENVFILE"
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$JWT_PW|" "$ENVFILE"
  sed -i "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=$ADMIN_PW|" "$ENVFILE"

  GENERATED_ENV=1
  say "已生成 $ENVFILE（随机数据库密码 / JWT / 超管密码，权限 600）"
  if docker volume ls -q 2>/dev/null | grep -qx 'blog-java_db_data'; then
    warn "检测到已存在的数据库卷 blog-java_db_data：新生成的 MySQL 密码不会自动改到旧库里。"
    warn "要么保留/找回旧 .env，要么先备份再 docker compose down -v 重建（会清库）。"
  fi
else
  chmod 600 "$ENVFILE" 2>/dev/null || true
  say "复用已有 $ENVFILE"
fi

for key in MYSQL_ROOT_PASSWORD MYSQL_USER MYSQL_PASSWORD MYSQL_DB JWT_SECRET ADMIN_PASSWORD; do
  require_env "$key"
done

MYSQL_DB="$(env_get MYSQL_DB)"
case "$MYSQL_DB" in
  ''|*[!A-Za-z0-9_]*) die "MYSQL_DB 只能包含字母、数字和下划线：$MYSQL_DB" ;;
esac
MYSQL_USER_VAL="$(env_get MYSQL_USER)"
case "$MYSQL_USER_VAL" in
  ''|*[!A-Za-z0-9_]*) die "MYSQL_USER 只能包含字母、数字和下划线：$MYSQL_USER_VAL" ;;
esac
[ "$MYSQL_USER_VAL" != "root" ] || die "MYSQL_USER 不能是 root（MySQL 镜像不允许）；改成 blog 之类普通账号"

APP_PORT="${APP_PORT:-$(env_get APP_PORT)}"; APP_PORT="${APP_PORT:-8888}"
WS_PORT="${WS_PORT:-$(env_get WS_PORT)}";   WS_PORT="${WS_PORT:-8889}"
HTTP_PORT="${HTTP_PORT:-$(env_get HTTP_PORT)}"; HTTP_PORT="${HTTP_PORT:-80}"
MAVEN_MIRROR="${MAVEN_MIRROR:-$(env_get MAVEN_MIRROR)}"
MAVEN_MIRROR="${MAVEN_MIRROR:-https://maven.aliyun.com/repository/public}"
export APP_PORT WS_PORT HTTP_PORT MAVEN_MIRROR

for port in "$APP_PORT" "$WS_PORT" "$HTTP_PORT"; do
  case "$port" in
    ''|*[!0-9]*) die "端口必须是数字：$port" ;;
  esac
  [ "$port" -ge 1 ] && [ "$port" -le 65535 ] || die "端口超出 1-65535：$port"
done
[ "$APP_PORT" != "$WS_PORT" ] || die "APP_PORT 与 WS_PORT 不能相同"
[ "$APP_PORT" != "$HTTP_PORT" ] || die "APP_PORT 与 HTTP_PORT 不能相同"
[ "$WS_PORT" != "$HTTP_PORT" ] || die "WS_PORT 与 HTTP_PORT 不能相同"

COMPOSE=(docker compose -f "$COMPOSE_FILE" --env-file "$ENVFILE")
PROFILE_ARGS=()
[ "${WITH_MUSIC:-0}" = "1" ] && PROFILE_ARGS=(--profile music)

say "校验 compose 配置"
"${COMPOSE[@]}" config >/dev/null || die "docker-compose.yml 配置校验失败（检查 $ENVFILE）"

# ---------- 3. 构建并启动 ----------
if [ "${SKIP_BUILD:-0}" = "1" ]; then
  say "跳过镜像构建（SKIP_BUILD=1）"
  "${COMPOSE[@]}" "${PROFILE_ARGS[@]}" up -d
else
  say "构建 Java 镜像并启动全栈（首次会下载 Maven 依赖，约 3-8 分钟）"
  "${COMPOSE[@]}" "${PROFILE_ARGS[@]}" up -d --build
fi

# ---------- 4. 等待就绪 ----------
say "等待后端就绪（首次启动要等 MySQL 初始化）"
READY=0
for i in $(seq 1 180); do
  if curl -fsS "http://127.0.0.1:${APP_PORT}/config" >/dev/null 2>&1; then
    echo "后端就绪（${i} 次探测，约 $((i * 2)) 秒）"
    READY=1
    break
  fi
  sleep 2
done

if [ "$READY" -ne 1 ]; then
  echo "----- db 最近 40 行日志 -----" >&2
  "${COMPOSE[@]}" logs --tail=40 db >&2 || true
  echo "----- blog-java-server 最近 80 行日志 -----" >&2
  "${COMPOSE[@]}" logs --tail=80 blog-java-server >&2 || true
  die "后端在 360 秒内没有就绪，按上面的日志排查（常见：MySQL 初始化未完成、端口被占用）"
fi

SITE_READY=0
for i in $(seq 1 15); do
  if curl -fsS "http://127.0.0.1:${HTTP_PORT}/" >/dev/null 2>&1; then
    SITE_READY=1
    break
  fi
  sleep 1
done
[ "$SITE_READY" -eq 1 ] || echo "[提示] 前端入口暂未返回 200，检查 ui/blog/dist 是否为空"

IP="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
[ -n "$IP" ] || IP="127.0.0.1"
PORT_SUFFIX=""
[ "$HTTP_PORT" != "80" ] && PORT_SUFFIX=":$HTTP_PORT"

printf '\n\033[1;32m==> 部署完成\033[0m\n'
cat <<EOF

  博客前台      http://$IP$PORT_SUFFIX/
  后台管理      http://$IP$PORT_SUFFIX/admin/     （账号 admin，密码见下）
  后端直连      http://$IP:$APP_PORT/
  接口文档      http://$IP:$APP_PORT/swagger
  WebSocket     ws://$IP:$WS_PORT/

  超管密码      $(env_get ADMIN_PASSWORD)
  环境变量      $ENVFILE
  查看日志      cd $COMPOSE_DIR && docker compose -f docker-compose.yml --env-file .env logs -f blog-java-server
  停止服务      cd $COMPOSE_DIR && docker compose -f docker-compose.yml --env-file .env down
  连库确认      docker compose -f $COMPOSE_FILE --env-file $ENVFILE exec db mysql -u$MYSQL_USER_VAL -p $MYSQL_DB -e 'show tables;'

EOF

if [ "$GENERATED_ENV" -eq 1 ]; then
  cat <<EOF
首次部署生成了随机超管密码，建议登录后立即在后台修改，或改 $ENVFILE 后重启：
  docker compose -f $COMPOSE_FILE --env-file $ENVFILE up -d

EOF
fi

cat <<EOF
提示：
  - 如果机器上还留着旧 Node 后端/旧栈，先停掉（都会占 80/8888/8889）。
  - Java 版上传只支持 oss / local / online，不支持 qiniu / minio。
  - 默认 oss；没有 OSS 密钥时上传会返回 100006，可先把 UPLOADTYPE 改成 local 只做联调。

EOF

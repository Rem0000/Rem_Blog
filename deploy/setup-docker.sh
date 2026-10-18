#!/usr/bin/env bash
# 一键部署：构建前端 -> 准备 .env -> docker compose 起全栈
# 适用于全新的 Ubuntu 22.04 / Debian 12 虚拟机。需要 root 或 docker 组权限。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER="$ROOT/blog-server"
ENVFILE="$SERVER/.env"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31m[错误] %s\033[0m\n' "$*" >&2; exit 1; }

command -v docker >/dev/null || die "未找到 docker，请先安装 Docker Engine 与 compose 插件"
docker compose version >/dev/null 2>&1 || die "未找到 docker compose 插件"

# 1. 前端产物
# 从 Windows/压缩包解出来的 .sh 通常没有可执行位，统一点上，再用 bash 调用兜底
chmod +x "$ROOT"/deploy/*.sh 2>/dev/null || true
say "构建前端（首次约 3-5 分钟）"
bash "$ROOT/deploy/build-frontends.sh"

# 2. .env：容器内要用 compose 的服务名连数据库
say "准备 blog-server/.env"
if [ -f "$ENVFILE" ]; then
  cp "$ENVFILE" "$ENVFILE.bak.$(date +%s)"
  echo "已备份原 .env"
fi
if grep -q '^MYSQL_HOST' "$ENVFILE" 2>/dev/null; then
  sed -i 's|^MYSQL_HOST.*|MYSQL_HOST = db|' "$ENVFILE"
else
  echo 'MYSQL_HOST = db' >> "$ENVFILE"
fi
grep -q '^MYSQL_PASSWORD' "$ENVFILE" || echo 'MYSQL_PASSWORD = 12345678' >> "$ENVFILE"
grep -q '^MYSQL_DB' "$ENVFILE" || echo 'MYSQL_DB = online_blog' >> "$ENVFILE"
grep -q '^JWT_SECRET' "$ENVFILE" || echo 'JWT_SECRET = change-me-in-production' >> "$ENVFILE"
grep -q '^APP_PORT' "$ENVFILE" || echo 'APP_PORT = 8888' >> "$ENVFILE"
echo "MYSQL_HOST 已设为 db（与 compose 中的服务名一致）"

# 3. 起服务
say "启动全部容器（首次会拉取 mysql/minio/nginx 镜像并编译后端）"
cd "$SERVER"
docker compose up -d --build

say "等待后端就绪"
for i in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1/api/config" >/dev/null 2>&1; then
    echo "后端就绪（${i}s）"
    break
  fi
  sleep 2
done

IP="$(hostname -I | awk '{print $1}')"
say "部署完成"
cat <<EOF

  博客前台     http://$IP/
  后台管理     http://$IP/admin/     （默认账号 admin，密码取 .env 的 ADMIN_PASSWORD）
  后端直连     http://$IP:8888/
  接口文档     http://$IP:8888/swagger
  MinIO 控制台 http://$IP:9999/

  查看日志：cd $SERVER && docker compose logs -f blog-server
  停止服务：cd $SERVER && docker compose down
  连库确认：docker compose exec db mysql -uroot -p12345678 online_blog -e 'show tables;'

EOF

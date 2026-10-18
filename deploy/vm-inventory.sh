#!/usr/bin/env bash
# 只读盘点脚本：不做任何删除，只输出旧服务留下的东西，供你决定清哪一步。
# 用法：bash vm-inventory.sh [可选：项目关键字，默认 tj-exam]
set -uo pipefail

KEY="${1:-tj-exam}"
line() { printf '\n\033[1;36m=== %s ===\033[0m\n' "$*"; }

line "主机与磁盘"
hostnamectl 2>/dev/null | sed -n '1,6p' || uname -a
df -h / /var 2>/dev/null | sort -u
echo "--- 根目录占用 Top10 ---"
du -xh --max-depth=1 / 2>/dev/null | sort -rh | head -10

line "Docker 版本与总账"
docker version --format 'server {{.Server.Version}}' 2>&1
docker system df
echo "--- 明细（镜像/容器/卷/构建缓存）---"
docker system df -v 2>/dev/null | head -60

line "Compose 项目"
docker compose ls -a 2>&1

line "所有容器（含已停止）"
docker ps -a --format 'table {{.ID}}\t{{.Image}}\t{{.Status}}\t{{.Names}}\t{{.Ports}}' 2>&1

line "所有镜像"
docker image ls --format 'table {{.Repository}}:{{.Tag}}\t{{.ID}}\t{{.Size}}\t{{.CreatedSince}}' 2>&1

line "所有卷（**这里有数据库数据，删前必须备份**）"
docker volume ls 2>&1

line "所有网络"
docker network ls 2>&1

line "与关键字 [$KEY] 相关的对象"
echo "--- 容器 ---"
docker ps -a --filter "name=$KEY" --format 'table {{.ID}}\t{{.Image}}\t{{.Status}}\t{{.Names}}' 2>&1
echo "--- 镜像 ---"
docker image ls --format '{{.Repository}}:{{.Tag}}\t{{.ID}}\t{{.Size}}' 2>&1 | grep -i "$KEY" || echo "(无)"
echo "--- 卷 ---"
docker volume ls --format '{{.Name}}' 2>&1 | grep -i "$KEY" || echo "(无)"

line "镜像被谁引用（解决 rmi conflict 用）"
for img in $(docker image ls --format '{{.Repository}}:{{.Tag}}' 2>/dev/null | grep -i "$KEY"); do
  echo "[$img]"
  docker ps -a --filter "ancestor=$img" --format '   容器 {{.ID}}  {{.Status}}  {{.Names}}' 2>&1 | head -20
done

line "端口占用（部署博客需要 80/3306/8888/8889/9000/9999/3000）"
if command -v ss >/dev/null; then
  ss -lntup 2>/dev/null | sed -n '1,40p'
else
  netstat -lntup 2>/dev/null | sed -n '1,40p'
fi

line "非 Docker 残留：systemd 服务"
systemctl list-units --type=service --state=running --no-pager --no-legend 2>/dev/null |
  awk '{print $1}' | grep -Ei "java|tj|exam|spring|nacos|redis|kafka|zookeeper|elastic|mysql|nginx|docker" || echo "(无匹配)"

line "非 Docker 残留：Java / Maven 进程与安装"
ps -eo pid,comm,args --sort=-%mem 2>/dev/null | grep -iE "java|maven|gradle" | grep -v grep || echo "(无 java 进程)"
command -v java && java -version 2>&1 | head -3
command -v mvn && mvn -v 2>&1 | head -2
echo "--- ~/.m2 / ~/.gradle 体积 ---"
du -sh ~/.m2 ~/.gradle 2>/dev/null || echo "(无)"

line "非 Docker 残留：nginx / 数据库"
command -v nginx >/dev/null && { echo "--- nginx 站点 ---"; ls -l /etc/nginx/sites-enabled/ /etc/nginx/conf.d/ 2>/dev/null; }
command -v mysql >/dev/null && echo "本机装了 mysql 客户端"
systemctl is-active mysql mysqld mariadb 2>/dev/null | paste -sd' ' -

line "非 Docker 残留：/opt /srv /home 下的大目录"
du -xh --max-depth=1 /opt /srv /var/lib/docker /var/log 2>/dev/null | sort -rh | head -15

line "日志占用"
journalctl --disk-usage 2>/dev/null

line "完毕（本脚本未修改任何东西）"

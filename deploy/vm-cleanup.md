# 旧虚拟机清理手册（Java 微服务 → 博客）

目标：把跑过 `tj-exam`（Java 微服务）的 VM 清成"只剩 Docker 引擎"的干净状态，再部署博客。
原则：**从外往里剥，每阶段结束都停下来看一眼**。容器可重建 → 镜像可重拉 → 卷删了就没救了。

```
第 0 阶段  盘点（只读）
第 1 阶段  停服务、删容器         ← 到这儿还能回滚（镜像和卷都在）
第 2 阶段  确认不需要回滚
第 3 阶段  删镜像
第 4 阶段  备份后删卷             ← 不可逆，最后一个删
第 5 阶段  清网络 / 构建缓存 / 日志
第 6 阶段  非 Docker 残留（systemd、nginx、JDK、Maven）
第 7 阶段  验收：端口 + 磁盘
```

---

## 第 0 阶段：盘点（只读，随时可跑）

```bash
# 把两个文件传到 VM 上，或直接在 VM 里写
bash vm-inventory.sh tj-exam | tee ~/inventory.txt
```

重点看四项：

1. `docker compose ls -a` —— 旧服务是不是 compose 起的，项目名是什么
2. `docker volume ls` —— 有没有数据库卷（名字常含 `mysql` / `data` / 项目名）
3. `ss -lntup` —— 80/3306/8888/8889/9000/9999/3000 有没有被占
4. `docker system df` —— 磁盘到底被谁吃了

**记下卷名**，第 4 阶段要用。

---

## 第 1 阶段：停服务、删容器

### 1.1 如果是 compose 起的（推荐方式，最干净）

```bash
cd /path/to/tj-exam            # 旧项目的 compose 文件所在目录
docker compose ls -a           # 确认项目名
docker compose down            # 只停并删容器 + 网络，保留镜像和卷
```

### 1.2 如果是 `docker run` 起的

```bash
docker ps -a --filter name=tj-exam
docker stop <容器ID或名字>
docker rm <容器ID或名字>
```

批量删（先看清单再删）：

```bash
# 先列出来核对
docker ps -a --filter "name=tj" --format '{{.ID}}  {{.Image}}  {{.Names}}'
# 确认无误再执行
docker ps -aq --filter "name=tj" | xargs -r docker rm -f
```

> 上一条 `docker rmi` 报的 conflict，就是这一步没做——容器还在，标签删不掉。

### 1.3 检查点

```bash
docker ps -a                  # 应该没有旧服务的容器了
docker system df              # 空间还没释放，正常，镜像和卷还在
```

---

## 第 2 阶段：确认是否需要回滚

停在这里问自己三个问题：

- 旧服务的**代码**还在本地/git 吗？（不在就先拉下来）
- 旧服务的**数据**以后还要吗？
- 有没有**配置**（`.env`、数据库连接串、证书）只存在于这台机器？

需要回滚的话，这一阶段就是最后机会：

```bash
# 重新起回来（compose）
docker compose up -d
```

想留个后路，可以把关键文件打包带走：

```bash
tar czf ~/tj-exam-config-$(date +%F).tar.gz \
  /path/to/tj-exam/.env /path/to/tj-exam/docker-compose.yml /etc/nginx/conf.d/
```

---

## 第 3 阶段：删镜像

先删最没风险的（悬挂镜像）：

```bash
docker image prune              # 只删 <none>，不影响有标签的
```

再删旧项目镜像：

```bash
docker image ls | grep -i tj-exam          # 核对
docker rmi tj-exam:latest                   # 容器已删，这时不会 conflict
docker rmi <其他还有的标签>
```

如果报 `image is being used by stopped container`，说明还有容器没删干净：

```bash
docker ps -a --filter ancestor=<镜像ID>
```

最后才是"所有没在用的镜像"（**注意：会连基础镜像一起删，下次部署要重新拉**）：

```bash
docker image prune -a
```

### 检查点

```bash
docker system df
docker image ls
```

---

## 第 4 阶段：备份后删卷（不可逆，最后做）

### 4.1 先备份

**MySQL 类卷**（容器里跑着就 dump，容器删了就从卷里拷）：

```bash
# 方式一：容器还在时直接 dump
docker run --rm -v <卷名>:/var/lib/mysql -v "$PWD":/backup alpine \
  sh -c 'tar czf /backup/mysql-$(date +%F).tar.gz -C /var/lib/mysql .'

# 方式二：容器还在跑，用 mysqldump 出可读 SQL
docker exec <mysql容器> mysqldump -uroot -p'<密码>' --all-databases > ~/all-db-$(date +%F).sql
```

**任意卷通用备份**：

```bash
docker run --rm -v <卷名>:/data -v "$PWD":/backup alpine \
  tar czf /backup/<卷名>-$(date +%F).tar.gz -C /data .
ls -lh backup/            # 确认文件非空再往下
```

### 4.2 删卷

```bash
docker volume ls                                    # 核对清单
docker volume rm <卷1> <卷2>                        # 逐个删，可控
```

确认里面确实全是垃圾，再一次性清空：

```bash
docker volume prune                                 # 删所有未被容器引用的卷
```

> ⚠️ `docker system prune --volumes` 会连卷一起删，别在这一阶段图省事用它。

### 检查点

```bash
docker volume ls
df -h /
```

---

## 第 5 阶段：清网络、构建缓存、日志

```bash
docker network ls
docker network rm <旧的业务网络>          # 比如 tj-exam_default
docker network prune                     # 或批量清未使用的

docker builder prune                     # 构建缓存，通常能回收几个 G
docker builder prune -a                  # 连所有缓存一起清

# 日志
journalctl --vacuum-size=200M
sudo find /var/lib/docker/containers -name '*-json.log' -size +100M -exec truncate -s 0 {} \;

# 系统包
sudo apt autoremove --purge -y
sudo apt clean
```

### 检查点

```bash
docker system df
df -h /
```

---

## 第 6 阶段：非 Docker 残留

Java 微服务常常还有宿主机上的东西：

```bash
# 1) systemd 服务
systemctl list-units --type=service --all | grep -Ei "tj|exam|java|spring"
sudo systemctl disable --now <服务名>
sudo rm /etc/systemd/system/<服务名>.service
sudo systemctl daemon-reload

# 2) 定时任务
crontab -l
sudo ls /etc/cron.d/ /etc/cron.daily/

# 3) nginx 旧站点
sudo ls -l /etc/nginx/sites-enabled/ /etc/nginx/conf.d/
sudo rm /etc/nginx/conf.d/<旧站点>.conf
sudo nginx -t && sudo systemctl reload nginx

# 4) 旧的部署目录
sudo du -xh --max-depth=1 /opt /srv /home | sort -rh | head
sudo rm -rf /opt/<旧项目>

# 5) 不再需要的 JDK / Maven 依赖（确认没有别的 Java 服务再用）
java -version
sudo apt remove --purge openjdk-* -y
rm -rf ~/.m2 ~/.gradle

# 6) 宿主机自装的 MySQL / Redis（如果这些以后由容器提供）
sudo systemctl disable --now mysql redis-server 2>/dev/null
sudo apt remove --purge mysql-server mysql-client redis-server -y
sudo rm -rf /var/lib/mysql /etc/mysql
```

> 第 5、6 条要谨慎：博客后端连的是**容器里的 MySQL**，宿主机自装的 MySQL 如果不用了可以删；但只要还有任何服务依赖它，就留着。

---

## 第 7 阶段：验收，准备部署博客

```bash
# 端口必须空闲：80 3306 8888 8889 9000 9999 3000
ss -lntup | grep -E ':(80|3306|8888|8889|9000|9999|3000)\b' || echo "端口全空闲 ✓"

# 磁盘
df -h /

# Docker 仍然可用（引擎本身不要卸）
docker version
docker compose version
```

确认干净后，部署博客：

```bash
cd /opt/blog
bash deploy/setup-docker.sh
```

---

## 一句话版

如果确定旧服务的**数据、代码、配置都不用留**，可以一条命令收尾（不可逆，最后才用）：

```bash
docker compose down --rmi all --volumes --remove-orphans   # 在旧项目目录里
docker system prune -a --volumes -f                        # 连同所有未使用对象
```

但既然是"逐渐清理"，就按上面的阶段走，每步都能停下来看一眼。

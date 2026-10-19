# syntax=docker/dockerfile:1
# Spring Boot 后端镜像（多阶段构建）
#
# 构建上下文必须是仓库根目录（my-blog/），因为要同时拿 blog-java-server 的源码和
# deploy/java/mvn-settings.xml。直接手动构建用：
#   docker build -f deploy/java/Dockerfile -t blog-java-server:1.0.0 .
#
# 注意：src/main/resources/application-local.yml 是本地开发配置（已 gitignore），
# 本镜像在打包前会删掉它，避免把本机数据库密码 / OSS 密钥打进镜像。
# 生产参数请通过环境变量注入，见 deploy/java/.env.example。

ARG MAVEN_MIRROR=https://maven.aliyun.com/repository/public

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
ARG MAVEN_MIRROR

ENV MAVEN_OPTS="-Xmx768m" \
    TZ=Asia/Shanghai

WORKDIR /build

# Maven 镜像，默认走阿里云；海外机器可传 --build-arg MAVEN_MIRROR=https://repo.maven.apache.org/maven2
COPY deploy/java/mvn-settings.xml /root/.m2/settings.xml
RUN sed -i "s|https://maven.aliyun.com/repository/public|${MAVEN_MIRROR}|" /root/.m2/settings.xml

# 先单独拷 pom 拉依赖，源码改动时能复用这一层缓存
COPY blog-java-server/pom.xml ./pom.xml
RUN mvn -B -q -s /root/.m2/settings.xml dependency:go-offline || true

COPY blog-java-server/src ./src

# 本地私有配置不能进镜像；部署参数一律用环境变量（SPRING_DATASOURCE_*、MYSQL_* 等）覆盖
RUN rm -f src/main/resources/application-local.yml \
 && mvn -B -s /root/.m2/settings.xml -Dmaven.test.skip=true package \
 && cp "$(ls target/blog-server-*.jar | head -n 1)" /build/app.jar

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    APP_PORT=8888 \
    CHAT_WS_PORT=8889 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8"

# curl 供 compose healthcheck 使用；tzdata 保证日志时区是 Asia/Shanghai
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends curl ca-certificates tzdata \
 && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
 && echo $TZ > /etc/timezone \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system blog \
 && useradd --system --gid blog --home-dir /app --no-create-home blog \
 && mkdir -p /app/upload \
 && chown -R blog:blog /app

WORKDIR /app

COPY --from=build --chown=blog:blog /build/app.jar /app/app.jar

# 本地落盘上传目录（UPLOADTYPE=local/online 时才用到），挂卷可持久化
VOLUME ["/app/upload"]

USER blog

EXPOSE 8888 8889

STOPSIGNAL SIGTERM

# 后台 /config 会查库：数据库没起来时容器保持 unhealthy，compose 不会先起 nginx
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${APP_PORT:-8888}/config" >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

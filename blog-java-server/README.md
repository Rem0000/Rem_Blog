# blog-server（Spring Boot + MyBatis-Plus 版）

原 `blog-server`（Node + Koa + Sequelize）的 Java 重写。**契约平移**：接口路径、请求体字段、响应 JSON 与现网保持一致，前端（`blog-v3` / `blog-v3-admin`）不需要任何改动。

## 技术栈

| 组件 | 版本 |
|---|---|
| JDK | 17 |
| Spring Boot | 3.2.12 |
| MyBatis-Plus | 3.5.9（`mybatis-plus-spring-boot3-starter` + `mybatis-plus-jsqlparser`） |
| MySQL | 8（复用原库，18 张表不动） |
| JWT | 手写 HS256（`javax.crypto.Mac`），**不依赖 jjwt**，原因见下 |
| 密码 | `spring-security-crypto` 的 BCrypt（不引入完整 Security） |
| 文档 | springdoc-openapi 2.6.0 |

> **两个依赖坑（已解决，别改回去）**
>
> 1. MyBatis-Plus 从 **3.5.9** 起把 `PaginationInnerInterceptor` 等依赖 JSqlParser 的插件拆到了独立模块，只引 `mybatis-plus-spring-boot3-starter` 会报「找不到符号 PaginationInnerInterceptor」。必须再加 `mybatis-plus-jsqlparser`。
> 2. jjwt 0.12 在**签发和验签两侧**都强制 HMAC 密钥 ≥ 256 位，而现网 Node 用的是 4 字节的 `JWT_SECRET=blog`，实测会抛
>    `The verification key's size is 32 bits which is not secure enough for the HS256 algorithm`。
>    为了迁移期两边互认 token，改成手写 HS256，行为与 Node 的 `jsonwebtoken` 一致（含 `iat`/`exp`、base64url、常量时间验签）。

## 运行

```bash
# 1. 打包
mvn -DskipTests clean package

# 2. 用环境变量启动（推荐）
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_USER=root
export MYSQL_PASSWORD=12345678
export MYSQL_DB=online_blog
export JWT_SECRET=blog
export ADMIN_PASSWORD=你的超管密码
export APP_PORT=8088
java -jar target/blog-server-1.0.0.jar

# 或者直接跑
mvn spring-boot:run
```

**本地开发**（推荐）：把密码写进 `src/main/resources/application-local.yml`，该文件已被 gitignore：

```yaml
spring:
  datasource:
    username: root
    password: 你的密码
```

由 `application.yml` 的 `spring.config.import: optional:classpath:application-local.yml` 自动加载，**不依赖启动时的工作目录**。也可以用上面的环境变量，优先级更高。

> 注意：`application-local.yml` 会被打进 jar（`BOOT-INF/classes/`）。本地开发没问题，**部署到服务器时请用环境变量 `MYSQL_PASSWORD` 覆盖**，不要把带密码的 jar 分发出去。

**重要**：Spring Boot **不会**读原项目的 `.env` 文件（那个文件里有行内注释，按 properties 解析会把注释当成值）。用 docker compose 时把环境变量写进 `environment:` 即可。

## 已实现

| 模块 | 接口 | 状态 |
|---|---|---|
| 基建 | 统一响应、错误码、全局异常、JWT、鉴权拦截器、MP 分页、CORS、Swagger | ✅ |
| config | `POST /config/update`、`GET /config/`、`PUT /config/addView` | ✅ |
| category | `add` / `update` / `delete` / `getCategoryList` / `getCategoryDictionary` | ✅ |
| tag | `add` / `update` / `delete` / `getTagList` / `getTagDictionary` | ✅ |
| pageHeader | `GET /pageHeader/getAll` | ✅ |
| statistic | `GET /statistic` | ✅ |
| user | `GET /user/getUserInfoById/{id}` | ⚠️ 只做了信息查询，登录/注册/改密未做 |
| article | `GET /article/blogHomeGetArticleList/{current}/{size}` | ⚠️ 只做了首页列表，其余 19 个接口未做 |
| links / comment / message / talk / photo / chat / 上传 | — | ⬜ 待做 |

**首页闭环已通**：`config` + `category` + `tag` + `pageHeader` + `statistic` + `user` + 文章列表，前台首页不再有 404。

## 命名与分页约定

| 层 | 约定 | 实现方式 |
|---|---|---|
| 数据库 | 下划线：`blog_name`、`created_at`、`view_time` | 建表脚本 `sql/online_blog.sql` |
| Java 实体 | 驼峰：`blogName`、`createdAt`、`viewTime` | MyBatis-Plus `table-underline` 自动映射 |
| JSON 响应 | 下划线：`blog_name`、`create_at`… | 全局 `spring.jackson.property-naming-strategy: SNAKE_CASE` |
| 分页入参 | `page` / `pageSize` | `PageQuery` DTO |
| 分页响应 | `{page, pageSize, total, list}` | `PageResult` |

**显式覆盖的四个例外**（前端既有的驼峰命名，不能跟着全局策略走）：

| 字段 | 出现在 |
|---|---|
| `createdAt` / `updatedAt` | 所有实体 |
| `pageSize` | 分页 DTO 与 `PageResult` |
| `categoryIdList` / `tagIdList` | `DeleteRequest` |

> 坑：全局命名策略**对反序列化同样生效**。不写 `@JsonProperty("pageSize")` 的话，Jackson 会去找 `page_size`，前端传的 `pageSize` 直接丢失（这个坑已经用 `JsonNamingTest` 锁住了）。

建表脚本在 `sql/online_blog.sql`（18 张表，已清理原库中 58 个由 Sequelize `sync({alter:true})` 反复改表留下的重复唯一索引）。

## 契约要点（改代码前必读）

1. **`code` 类型是混的**：成功是数字 `0`，提示是数字 `100`，错误是**字符串** `"100016"`。所以 `Result.code` 声明成 `Object`，别改成 `int`。
2. **HTTP 状态码有意义**：`100002`→403、`100016`→401（前端看到 401 会清 token 并弹登录）、其余→500。
3. **命名约定**：数据库下划线、Java 驼峰、JSON 下划线（全局 `SNAKE_CASE` 策略）。例外只有 `createdAt` / `updatedAt` / `pageSize` / `categoryIdList` / `tagIdList`，它们在前端是驼峰，靠 `@JsonProperty` 显式覆盖。
4. **`createdAt` / `updatedAt` 的 JSON 名保持驼峰**，前端 11 个文件在读（评论、文章列表、时间轴）。数据库列名已统一成 `created_at` / `updated_at`。
5. **分页统一为 `page` / `pageSize`**（1 基），响应是 `{page, pageSize, total, list}`；`page`/`pageSize` 用 `Object` 原样回显，前端传字符串也能拿回。
6. **JWT 兼容裸 token**：`Authorization` 头可能带 `Bearer ` 也可能不带，拦截器两种都认。
7. **JWT 必须按原始字节做 HMAC**：默认 `JWT_SECRET=blog` 只有 4 字节，jjwt 会拒绝签发/验签，所以 `JwtUtil` 用 `javax.crypto.Mac` 手写，才能与 Node 签出的 token 互通（已实测）。
8. **字典接口不带时间字段**：`getCategoryDictionary` / `getTagDictionary` 只返回 `id` + 名称，对齐原实现的 `attributes` 限制。

## 已知差异

- **分类改名校验保留了原实现的怪逻辑**：`verifyCategory` 里用的是 `res.id == id`（同名即报"分类已存在"），而 tag 用的是 `!==`（逻辑正确）。这里照抄以保持行为一致，代码中有注释说明；要修就把 `equals` 改成 `!equals`。
- **限流未实现**：`/config/addView`（60 分钟 100 次）和全局 200 次/分钟，属于批次 5，切流前必须补。
- **上传未实现**：`/config/update` 里删除旧图的那段逻辑依赖上传模块，暂时跳过。

## 验证建议

先用现网的 golden 响应做比对，重点看三个接口：

```bash
curl -s http://127.0.0.1:8088/config/ | jq          # 字段名、时间格式、code 类型
curl -s "http://127.0.0.1:8088/category/getCategoryDictionary" | jq
curl -s -X POST http://127.0.0.1:8088/category/getCategoryList \
  -H 'Content-Type: application/json' -d '{"current":"1","size":"10"}' | jq
```

第三个请求重点确认 `current`/`size` 回显的是 `"1"`/`"10"`（字符串）还是 `1`/`10`（数字）——这决定后台分页组件是否正常。

## 验证状态

已在本机实际跑过（Maven 3.8.4 + JDK 17）：

| 项 | 结果 |
|---|---|
| `mvn package` | ✅ BUILD SUCCESS，33 个源文件，产出 33 MB 可执行 jar |
| Spring 上下文启动 | ✅ Tomcat 起在配置端口 |
| 错误响应体 | ✅ 500 + `{"code":"100007","message":"获取网站设置失败"}`（无 `result` 字段） |
| 404 响应 | ✅ `{"code":"404","message":"接口没有找到"}` |
| 未带 token | ✅ 401 + `{"code":"100016","message":"您没有权限访问，请先登录"}` |
| Node 签发的 token（4 字节密钥） | ✅ 验签通过，进入业务逻辑 |
| 过期 token | ✅ 401 + `{"code":"100016","message":"token已过期"}` |
| 伪造签名 | ✅ 403 + `{"code":"100002","message":"无效的token"}` |
| Swagger | ✅ `/v3/api-docs` 列出全部接口 |
| JSON 命名策略（`JsonNamingTest`） | ✅ 4 个用例通过：`category_name` 转下划线、`createdAt` 保持驼峰、`page`/`pageSize` 收发正常、`categoryIdList` 不被转成 `category_id_list` |
| 数据库真实读写 | ✅ 在临时库灌演示数据实测通过（见下） |
| `GET /statistic` | ✅ `{"articleCount":2,"tagCount":2,"categoryCount":2,"userCount":1}`（驼峰） |
| `GET /pageHeader/getAll` | ✅ `[{"id":1,"route_name":"Home","bg_url":"..."}]`（下划线，无时间戳） |
| `GET /user/getUserInfoById/1` | ✅ `{"id":1,"role":1,"nick_name":"Rem","ipAddress":"四川"}`（混驼峰，不含 password） |
| `GET /article/blogHomeGetArticleList/1/10` | ✅ 分类名与标签数组组装正确，`createdAt` 格式为 `2026-10-18 19:09:39` |
| `blog_config.changed` 时间格式 | ✅ `2026-10-18 19:09:38`（无 `T`） |

> **踩过的坑**：`ArticleListItem` 一开始只写了 `@JsonProperty("createdAt")`，漏了
> `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`，结果输出成 `2026-10-18T19:09:39`。
> 新建任何带时间字段的 DTO 都要两个注解一起写。

> **联调用的演示数据**：`sql/seed-demo.sql`（2 篇文章、2 个分类、2 个标签、1 个用户、1 张背景图）。
> 导入命令：`mysql -uroot -p online_blog < sql/seed-demo.sql`。不导入也不影响接口，只是列表为空。

> `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 是为了绕过某个受限环境下
> Mockito 加载字节码代理失败的问题（`Failed to load ... MockMaker`）。正常开发机上可以删掉，
> 删掉后会恢复默认的 inline mock maker。

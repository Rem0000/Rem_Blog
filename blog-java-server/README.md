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
| pageHeader | `GET /pageHeader/getAll`、`POST /pageHeader/addOrUpdate`、`POST /pageHeader/delete` | ✅ |
| statistic | `GET /statistic` | ✅ |
| user | `register`（1 分钟 3 次限流）/ `login` / `updateOwnUserInfo` / `updatePassword` / `updateRole` / `adminUpdateUserInfo` / `getUserList` / `getUserInfoById` | ✅ |
| article | 首页 / 时间轴 / 详情 / 增删改 / 置顶 / 公开私密 / 标题查重 / 按标签 / 按分类 / 推荐 / 搜索 / 热门 / 点赞 / 阅读时长 / updateUrl，共 20 个 | ✅ |
| links | `add`（1 分钟 5 次限流）/ `frontUpdate` / `backUpdate` / `delete` / `approve` / `getLinksList` | ✅ |
| like / notify | `like/addLike`（1 分钟 10 次）/ `like/cancelLike`（1 分钟 10 次）/ `like/getIsLikeByIdOrIpAndType` / `notify/update` / `notify/delete` / `notify/getNotifyList` | ✅ |
| comment / message / talk / photo / chat / 上传 | — | ⬜ 待做 |

**首页闭环已通**：`config` + `category` + `tag` + `pageHeader` + `statistic` + `user` + 文章列表，前台首页不再有 404。

## 命名与分页约定

| 层 | 约定 | 实现方式 |
|---|---|---|
| 数据库 | 下划线：`blog_name`、`created_at`、`view_time` | 建表脚本 `sql/online_blog.sql` |
| Java 实体 | 驼峰：`blogName`、`createdAt`、`viewTime` | MyBatis-Plus `table-underline` 自动映射 |
| JSON 响应 | 下划线：`blog_name`、`create_at`… | 全局 `spring.jackson.property-naming-strategy: SNAKE_CASE` |
| 分页入参 | `page` / `pageSize` | `PageQuery` DTO |
| 分页响应 | `{page, pageSize, total, list}` | `PageResult` |

> **分页现状（重要）**：body 分页并非所有接口都是 `page`/`pageSize`。
> `links/getLinksList`、`category/getCategoryList`、`tag/getTagList` 的现网契约都是
> **`current`/`size`**（前台 `link-list.vue`、后台 `links/index.ts` / `category/index.tsx` / `tag/index.tsx` 都在传），
> 响应是 `{current, size, list, total}`，已实测 `mrzym.top` 同此；`page`/`pageSize` 仅作为兼容别名保留
> （两者都有时以 `current`/`size` 为准）。

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
- **背景图改名修正了原实现的崩溃**：`pageHeader/addOrUpdate` 在“改成一个未被占用的 route_name”时，Node 的 `getOneByPath` 返回 null 后直接读 `flag.id` 会抛 TypeError（500）。这里改成允许改名，其余唯一性校验行为不变。
- **文章按标签筛选修正了原实现的退化**：`getArticleList` 传了一个没有任何文章的 `tag_id` 时，Node 会因为 `articleIdList` 为 null 跳过过滤，把全部文章返回；这里按语义返回空列表。
- **`article/updateUrl` 修正为可用**：原实现把 service 直接当 handler 用、没有设置 `ctx.body`，现网访问是 404 空响应；这里补成真正执行封面域名替换并返回受影响行数。
- **首页/时间轴的 current/size 回显字符串**：现网是路径参数，响应里是 `"1"`/`"2"`；首页实现之前回显的是数字，已一并对齐。
- **IP 属地库未接**：Node 用 `xz-ipnet` 把 IP 转成省份；Java 侧暂未引入离线库。`IpUtil.locate` 目前对“库里的值是 IP”统一返回「本机地址」（与 Node 查不到归属时的兜底一致），对历史数据里已经是「四川」这种属地文本的原样返回。接 `ip2region` 后只需改 `IpUtil.locate`。
- **昵称敏感词兜底改为内置短语**：Node 命中敏感词/脚本关键字后会去 iciba 接口取「每日一句」（依赖外网）；这里用内置短语，避免注册被外部接口拖死。
- **头像/封面远程删除未接**：`updateOwnUserInfo`、`adminUpdateUserInfo`、`article/update` 里 qiniu/minio 模式的删图逻辑依赖上传模块，暂时只打日志，等 utils 上传一起补。
- **限流基础设施已就绪**：`@RateLimit` + 进程内固定窗口（`limit/RateLimitStore`、`limit/RateLimitInterceptor`），已挂在 `/links/add`（1 分钟 5 次）。超限行为与现网实测一致：429 + `{"message":"新增过于频繁 请稍后再试"}` + `X-Ratelimit-Limit/Remaining/Reset` + `Retry-After`。`/config/addView`（60 分钟 100 次）和全局 200 次/分钟尚未挂上，切流前必须补。
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
| `mvn package` | ✅ BUILD SUCCESS，80+ 个源文件，产出 33 MB 可执行 jar |
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
| `POST /links/add` | ✅ `{"code":0,"message":"发布友链成功","result":true}`，并写入 `blog_notify`（type=4、is_view=1） |
| `/links/add` 限流 | ✅ 第 6 次 429，body/头与现网一致（`{"message":"新增过于频繁 请稍后再试"}`、`X-Ratelimit-*`、`Retry-After`） |
| `POST /links/getLinksList` | ✅ `{current,size,list,total}`（字段顺序也对齐现网），`createdAt` 无 `T`，`created_at` 升序；status/site_name/time 过滤与空值边界均通过 |
| `PUT /links/approve` / `delete` | ✅ approve 返回 `[affectedCount]`（对齐 Sequelize），delete 命中 0 条时 `result:null`、命中时返回条数 |
| 友链鉴权 | ✅ 无 token 401/`100016`；admin 超管 403/`100002`（ADMIN_NOT_SUPER） |
| `POST /pageHeader/addOrUpdate` | ✅ 新增/修改均 `result:true`；route_name 重复时 500 + `100013`「已经存在相同的背景路径」；改名到空闲路径可用（原实现会崩，见已知差异） |
| `POST /pageHeader/delete` | ✅ 命中返回条数，重复删除 `result:null`；`GET /pageHeader/getAll` 仍只出 `id/route_name/bg_url` |
| `GET /article/getArticleById/1` | ✅ 21 个字段顺序/命名与现网一致，`tagIdList`/`tagNameList` 按关联顺序，`view_times` 每次访问 +1（响应即加后值） |
| `GET /article/blogHomeGetArticleList/1/2` | ✅ `current`/`size` 回显字符串，列表项顺序与现网一致（createdAt/updatedAt 在最前） |
| 时间轴 / 按标签 / 按分类 | ✅ 时间轴按年分组 `{year,articleList}`；按标签/分类 `current/size` 回显与现网一致；标签无文章返回空 |
| 推荐 / 搜索 / 热门 | ✅ 上一篇/下一篇回退当前文章、同标签最多 6 篇；搜索片段截取 `关键词长度+12`；热门按 `view_times` 前 5 |
| `POST /article/getArticleList` | ✅ 需登录；默认 `status in (1,2)`，排除 `article_content`/`origin_url`，标题/置顶/状态/分类/标签/时间筛选均通过 |
| 文章增删改 | ✅ 标题查重、分类按名新建、标签按名新建并重建关联；`delete/{id}/1` 进回收站、`/3` 永久删除并清关联；返回结构 `{article,articleTagList}` / `{res,newArticleTagList}` |
| 置顶/公开/恢复/点赞/阅读时长 | ✅ 消息文案与现网一致（含 `isPublic` 按“当前状态”取名），阅读时长按毫秒累加 |
| `POST /user/register` | ✅ 用户名仅字母数字、admin 保留、重复校验；密码落库为 `$2a$10$` bcrypt（60 位）；返回 `{id,username}`；1 分钟 3 次限流 |
| `POST /user/login` | ✅ 普通用户/超管两条分支；超管返回 `{token,username:"超级管理员",role:1,id:5201314}`，普通用户多 `ipAddress`；错误文案 = 用户名不存在/密码不匹配/密码错误 |
| 改资料 / 改密码 | ✅ `updatePassword` 校验两次一致、旧密码匹配、id=2 保护；改完可用新密码登录 |
| `getUserList` | ✅ 需登录；`ip` 原样 + `ip_address` 追加（注意是下划线），role 仅在数字非 0 时过滤 |
| `getUserInfoById/5201314` | ✅ 只回 `{id,role,nick_name:"超级管理员"}`；普通用户回 `ipAddress` 驼峰 |
| `like` 三个接口 | ✅ 登录用户按 `user_id`、游客按 `ip` 维度判重；重复/未点赞错误文案与现网一致；点赞数同步到文章 `thumbs_up_times`、说说/留言 `like_times`、评论 `thumbs_up` |
| `notify` 三个接口 | ✅ 列表按 `is_view` 升序 + `created_at` 倒序，`userId` 驼峰入参；已阅返回 boolean；删除返回 `{res:条数}` |

> **踩过的坑**：`ArticleListItem` 一开始只写了 `@JsonProperty("createdAt")`，漏了
> `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`，结果输出成 `2026-10-18T19:09:39`。
> 新建任何带时间字段的 DTO 都要两个注解一起写。

> **联调用的演示数据**：`sql/seed-demo.sql`（2 篇文章、2 个分类、2 个标签、1 个用户、1 张背景图）。
> 导入命令：`mysql -uroot -p online_blog < sql/seed-demo.sql`。不导入也不影响接口，只是列表为空。

> `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 是为了绕过某个受限环境下
> Mockito 加载字节码代理失败的问题（`Failed to load ... MockMaker`）。正常开发机上可以删掉，
> 删掉后会恢复默认的 inline mock maker。

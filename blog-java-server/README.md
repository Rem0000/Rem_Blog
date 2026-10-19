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
| 图片存储 | 阿里云 OSS（`aliyun-sdk-oss` 3.18.1），本地 `local` 作为调试兜底 |
| IP 属地 | `mica-ip2region` 3.2.12（内置 ip2region v4 + ipv6wry 数据，Apache-2.0，无需外网） |
| 文档 | springdoc-openapi 2.6.0 |

> **三个依赖坑（已解决，别改回去）**
>
> 1. MyBatis-Plus 从 **3.5.9** 起把 `PaginationInnerInterceptor` 等依赖 JSqlParser 的插件拆到了独立模块，只引 `mybatis-plus-spring-boot3-starter` 会报「找不到符号 PaginationInnerInterceptor」。必须再加 `mybatis-plus-jsqlparser`。
> 2. jjwt 0.12 在**签发和验签两侧**都强制 HMAC 密钥 ≥ 256 位，而现网 Node 用的是 4 字节的 `JWT_SECRET=blog`，实测会抛
>    `The verification key's size is 32 bits which is not secure enough for the HS256 algorithm`。
>    为了迁移期两边互认 token，改成手写 HS256，行为与 Node 的 `jsonwebtoken` 一致（含 `iat`/`exp`、base64url、常量时间验签）。
> 3. `aliyun-sdk-oss` 通过 Apache HttpClient 带进了 `commons-logging`，Spring Boot 用 `spring-jcl` 提供同名 API，两个 jar 同时存在会打冲突警告。`pom.xml` 里已经对 `commons-logging` 做了 exclusion，别删。

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
# 阿里云 OSS（上传图片用）
export UPLOADTYPE=oss
export ALIBABA_CLOUD_ACCESS_KEY_ID=你的AccessKeyId
export ALIBABA_CLOUD_ACCESS_KEY_SECRET=你的AccessKeySecret
export OSS_ENDPOINT=oss-cn-shanghai.aliyuncs.com
export OSS_BUCKET_NAME=你的Bucket
# 可选：自定义域名 / CDN
# export OSS_DOMAIN=img.example.com
java -jar target/blog-server-1.0.0.jar

# 或者直接跑
mvn spring-boot:run
```

**本地开发**（推荐）：把密码和 OSS 密钥写进 `src/main/resources/application-local.yml`，该文件已被 gitignore：

```yaml
spring:
  datasource:
    username: root
    password: 你的密码

blog:
  upload-type: oss

oss:
  endpoint: oss-cn-shanghai.aliyuncs.com
  bucket-name: 你的Bucket
  access-key-id: 你的AccessKeyId
  access-key-secret: 你的AccessKeySecret
```

由 `application.yml` 的 `spring.profiles.active: local` 自动加载，**不依赖启动时的工作目录**。也可以用上面的环境变量，优先级更高。

> 注意：`application-local.yml` 会被打进 jar（`BOOT-INF/classes/`）。本地开发没问题，**部署到服务器时请用环境变量 `MYSQL_PASSWORD` 覆盖**，不要把带密码的 jar 分发出去。

**重要**：Spring Boot **不会**读原项目的 `.env` 文件（那个文件里有行内注释，按 properties 解析会把注释当成值）。用 docker compose 时把环境变量写进 `environment:` 即可。

## 图片存储（阿里云 OSS）

`POST /upload/img` 支持三种模式（`blog.upload-type` / `UPLOADTYPE`）：

| 模式 | 行为 |
|---|---|
| `oss` | 上传到阿里云 OSS，返回 `https://<bucket>.<endpoint>/<dir>/<uuid>.<ext>`（或自定义域名） |
| `local` | 落盘 `upload/local`，返回 `http://127.0.0.1:<port>/local/<file>`，仅本地调试 |
| `online` | 落盘 `upload/online`，返回 `/online/**` URL |

OSS 关键配置：

| 配置 | 环境变量 | 说明 |
|---|---|---|
| `oss.endpoint` | `OSS_ENDPOINT` | 如 `oss-cn-shanghai.aliyuncs.com` |
| `oss.bucket-name` | `OSS_BUCKET_NAME` | Bucket 名称 |
| `oss.access-key-id` | `ALIBABA_CLOUD_ACCESS_KEY_ID` | AccessKey ID |
| `oss.access-key-secret` | `ALIBABA_CLOUD_ACCESS_KEY_SECRET` | AccessKey Secret |
| `oss.domain` | `OSS_DOMAIN` | 可选，自定义域名 / CDN |
| `oss.dir` | `OSS_DIR` | 对象前缀，默认 `images/` |
| `oss.referer` | `OSS_REFERER` | 防盗链用的 Referer；留空时自动用上传请求的 Origin / Referer |
| `oss.public-read` | `OSS_PUBLIC_READ` | 上传时把对象 ACL 设为公共读，默认 `true` |

> **Bucket 权限**：OSS 新建 Bucket 默认私有，匿名访问会 403。上传代码在 `oss.public-read=true`（默认）时会把对象设为 `public-read`；如果 Bucket 开启了「阻止公共访问」，需要先在控制台关闭（或把 Bucket 读权限改成公共读），否则上传会返回 `AccessDenied`。CDN 场景请把 `oss.domain` 配成绑定域名。
>
> 图片文件名是 UUID，内容不会变，上传时带了 `Cache-Control: public, max-age=31536000`，可直接走 OSS/CDN 缓存。
>
> **安全提醒**：`POST /upload/img` 保持原实现的契约，不强制登录，只有按 IP 的 100 次/分钟限流。公网部署时建议给 `UploadController` 加 `@RequireRole(RequireRole.Level.LOGIN)`，并在 OSS 配防盗链，避免被匿名刷上传量。

### 防盗链（Referer 白名单）

OSS 控制台 → 目标 Bucket → **数据安全 → 防盗链**：

- 白名单填**页面域名**（不是 OSS 图片域名），HTTP / HTTPS 各写一条，带端口的也要写：
  ```
  https://your-blog.com
  http://your-blog.com
  https://admin.your-blog.com
  http://localhost:8080
  http://127.0.0.1:8080
  http://localhost:8848
  http://127.0.0.1:8848
  ```
  （`8080` 是 `blog-v3` 开发端口，`8848` 是 `blog-v3-admin` 开发端口）
- 「空 Referer」：严格防刷可以选“不允许”；但这样直接在浏览器地址栏打开 OSS 链接会 403，部分移动端/小程序也可能不带 Referer。不确定时先选“允许”。
- 匹配不忽略协议（`http` 和 `https` 要分别配），支持 `*` / `?` 通配符。

代码侧的处理：

- 浏览器加载图片时会自动带 Referer；两个前端的 `index.html` 已加 `<meta name="referrer" content="strict-origin-when-cross-origin">`，跨域时至少会带 `https://你的域名`。
- 后端上传是带 `Authorization` 的 SDK 请求，按[阿里云文档](https://help.aliyun.com/zh/oss/user-guide/0003-00000501)**不触发防盗链**；但本项目仍会给 `PutObjectRequest` 补 `Referer`：优先用 `oss.referer`，没配就用上传请求自身的 `Origin` / `Referer`。
- 前台、后台、本地调试域名比较多时，建议直接配 `oss.referer` 成白名单里的主域名，最稳：
  ```yaml
  oss:
    referer: https://your-blog.com
  ```
- `oss.domain` 建议用 HTTPS：HTTPS 页面引用 HTTP 图片时，浏览器会按降级策略不发 Referer，而且会被混合内容拦截。

**环境变量不生效？** Windows 修改系统环境变量只对**之后新启动的进程**生效：已经打开的 IDEA、终端、Maven 进程不会刷新，必须完全退出再打开（只点“重新运行”没用）。启动日志里会打印：

- `OSS 配置已加载: endpoint=..., bucket=..., accessKeyId=LTAI***xx`：说明当前进程读到了密钥
- `OSS 配置未完整加载: ... accessKeyId=缺失 ...`：说明当前进程没有继承到环境变量

如果不想折腾环境变量，直接写进 `application-local.yml`（已 gitignore）最省事；或者在 IDEA 的 `Run -> Edit Configurations -> Environment variables` 里单独给这个启动配置加变量。

**旧图清理**：替换/删除头像、文章封面、说说图片、相册封面、相册照片、页面背景、聊天图片时，会按 URL 反推 OSS key 删除旧对象。只有 `UPLOADTYPE=oss` 才执行；只删当前 Bucket / `oss.domain` 下、位于 `oss.dir` 前缀（默认 `images/`）的对象；删除前会确认没有其他记录再引用，外部 URL（如默认头像 `mrzym.top/online/...`）自动跳过。删除是 best-effort，失败只打日志，不影响主流程。

**旧图片**：`upload/local` 下已有的文件不会被自动搬运；切到 OSS 后旧 URL 仍由 `/local/**` 静态映射提供服务。要彻底迁移，把文件上传到 OSS 的 `images/` 目录，再把数据库里的 `http://127.0.0.1:8088/local/` 替换成 OSS 地址即可（文章封面 `blog_article.article_cover`、正文 `blog_article.article_content`，其他表同理）：

```sql
-- 把 <bucket> / <endpoint> 换成实际值；旧 URL 的端口按实际情况调整
UPDATE blog_article
SET article_cover = REPLACE(article_cover,
        'http://127.0.0.1:8088/local/',
        'https://<bucket>.<endpoint>/images/')
WHERE article_cover LIKE 'http://127.0.0.1:8088/local/%';

UPDATE blog_article
SET article_content = REPLACE(article_content,
        'http://127.0.0.1:8088/local/',
        'https://<bucket>.<endpoint>/images/')
WHERE article_content LIKE '%http://127.0.0.1:8088/local/%';
```

## IP 属地

使用 `mica-ip2region` 3.2.12（Apache-2.0），依赖内已包含 `ip2region/ip2region.xdb`（IPv4）和 `ip2region/ipv6wry.db`（IPv6），默认自动配置，无需额外配置或下载数据。

- 中国 IP 返回省份，如「江苏」「广东」「北京」
- 取不到省份时返回国家，如「美国」
- 内网 / 回环 / 解析失败返回「本机地址」
- 历史数据里已经是「四川」这种属地文本的原样返回

需要替换数据文件时，在 `application.yml` 里配置：

```yaml
mica:
  ip2region:
    db-file-location: classpath:ip2region/ip2region.xdb
    ipv6db-file-location: classpath:ip2region/ipv6wry.db
```

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
| comment | `add`（1 分钟 20 次）/ `apply`（1 分钟 20 次）/ `thumbUp`（10 次）/ `cancelCommentLike`（10 次）/ `delete` / `backDelete` / `backGetCommentList` / `frontGetParentComment` / `frontGetChildrenComment` / `getCommentTotal` | ✅ |
| message | `add`（5 次）/ `update`（5 次）/ `delete`（5 次）/ `backDelete` / `like`（5 次）/ `cancelLike`（5 次）/ `getMessageList` / `getAllMessage` / `getHotTagList` | ✅ |
| talk | `publishTalk` / `updateTalk` / `deleteTalkById` / `togglePublic` / `toggleTop` / `revertTalk` / `getTalkList` / `getTalkById` / `like`（5 次）/ `cancelLike`（5 次）/ `blogGetTalkList` | ✅ |
| photo / photoAlbum | `photo/add` / `photo/delete` / `photo/revert` / `photo/getPhotoListByAlbumId` / `photo/getAllPhotosByAlbumId`；`photoAlbum/add` / `delete` / `update` / `/`（分页）/ `getAllAlbumList` | ✅ |
| utils / 根路由 | `POST /upload/img`（1 分钟 100 次）；`GET /` 欢迎页 | ✅（oss / local / online） |
| chat | `add`（登录）/ `deleteOne` / `delete`（管理员，TRUNCATE）/ `getChatList` + 独立端口 8889 的 WebSocket（`init`/`message`/`revert`/`offline`/`onlineList`） | ✅ |

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
- **IP 属地已接离线库**：Node 用 `xz-ipnet`；Java 改用 `mica-ip2region`（ip2region v4 + ipv6wry 数据已打进依赖，Apache-2.0），`IpLocationService` 返回「江苏」「广东」「北京」这类省份，取不到省份时返回国家；内网/回环/解析失败返回「本机地址」，历史数据里已经是「四川」这种属地文本的原样返回。
- **`IpUtil.stripPort` 修正了 IPv6 截断**：原 Node 的 `ip.split(":").pop()` 会把裸 IPv6 截成最后一段；Java 现在只对 `IPv4:port` / `[IPv6]:port` 去端口，裸 IPv6 原样保留。
- **昵称敏感词兜底改为内置短语**：Node 命中敏感词/脚本关键字后会去 iciba 接口取「每日一句」（依赖外网）；这里用内置短语，避免注册被外部接口拖死。
- **头像/封面 OSS 旧图已接删除**：`updateOwnUserInfo`、`adminUpdateUserInfo`、`article/update`、`article/delete(status=3)` 会在更新/删除成功后删旧 OSS 对象；外部地址（默认头像、历史 `mrzym.top` 图片等）自动跳过，删除前会确认没有其他记录再引用。
- **评论后台的 from_name/to_name 过滤修正**：原实现把列名写成了不存在的 `name`，一旦传这两个筛选就 500；这里改成按 `from_name` / `to_name` 列 like 查询。
- **评论错误码照抄原实现的 bug**：`controller/comment` 里 `errorCode` 用的是 `ERRORCODE.CATEGORY`（100004），不是 COMMENT（100015），评论相关错误现在仍返回 100004。
- **评论出参里的 `form_name`**：原实现把 `from_name` 拼成了 `form_name`，前端评论列表真的在读这个 key，所以保留；`is_like` 只在点赞状态场景出现。
- **`message/getHotTagList` 现网是 404**：本地 Node 源码里有这个路由，但线上部署版本没有（实测 `GET http://mrzym.top/api/message/getHotTagList` 返回 404）。这里按源码实现成可用接口，前端目前也没依赖 404。
- **留言游客昵称**：原实现 `randomNickname("游客", 5)`；这里生成「游客 + 5 位数字/字母」，前缀和长度一致。
- **`talk/getTalkById` 的时间是 ISO UTC**：原实现用 `{...res.dataValues}` 展开，Date 直接 JSON 化成 `2026-10-18T11:45:57.000Z`；列表接口才是 `yyyy-MM-dd HH:mm:ss`。这里照现网分开处理，别“顺手统一”。
- **说说删除的返回类型不统一**：进回收站（status 1/2）返回 `[count]`（Sequelize update 数组），永久删除（status 3）返回数字 `count`；已照抄。
- **相册/图片删除的返回类型同理不统一**：图片 `type=1` 回收站返回 `[count]`、`type=2` 返回数字；相册删除返回 boolean。
- **相册分页路径是 `/photoAlbum/`**（带斜杠），Controller 用 `@PostMapping({"", "/"})` 同时兼容。
- **上传三模式现状**：`oss` 已接入阿里云 OSS（`aliyun-sdk-oss`，对象默认公共读，URL 为 `https://<bucket>.<endpoint>/images/<uuid>.<ext>` 或 `oss.domain`）；`local` 落盘 `upload/local`，通过 `/local/**` 静态访问；`online` 落盘 `upload/online` 并返回 `/online/**` 的 URL（Node 的 online 分支没写 body，是 404，这里补成可用）；`qiniu` / `minio` 未实现，配置成这两个值会返回 `100006`「当前上传模式未配置」。
- **本地落盘目录按进程 cwd 解析**：`upload/local` / `upload/online` 相对启动目录（与 Node 的 `__dirname/upload` 不同），部署时请固定工作目录或改成绝对路径；OSS 模式不受影响。
- **聊天的 createdAt/updatedAt 是 ISO UTC**：原实现改的是 `message.dataValues`，Date 绕过了 model 的 getter，输出形如 `2026-10-10T04:06:11.000Z`；且 `size` 是本次返回条数、`list` 里可能出现 `null`（历史脏数据 user_id 为空），均已照抄。
- **聊天删除接口没有 result 字段**：`deleteOne` / `delete` 的响应只有 `{code,message}`（原实现 `result()` 只传一个参数，undefined 被 JSON.stringify 丢掉）。
- **WebSocket 用 Java-WebSocket 实现**：Spring 自带 WebSocket 只能挂主端口，聊天要求独立 8889，所以引入 `org.java-websocket:Java-WebSocket` 单独起服务；协议与 Node 的 ws 版本一致。构建时若本机 `D:\Java\MavenRepority` 不可写，用 `-s tools/mvn-settings.xml` 把本地仓库指到工作区。
- **限流已全部就绪**：`@RateLimit` + 进程内固定窗口（`limit/RateLimitStore`、`limit/RateLimitInterceptor`），已挂 `/links/add`（5 次/分）、`/user/register`（3 次/分）、`/like/addLike`、`/comment/*`、`/message/*`、`/talk/like`、`/upload/img`（100 次/分）、`/config/addView`（60 分钟 100 次）；全局 200 次/分钟由 `limit/GlobalRateLimitFilter` 实现。超限行为与现网一致：429 + `{"message":"..."}` + `X-RateLimit-*` + `Retry-After`（固定为窗口长度，同 koa2-ratelimit）。
- **OSS 旧图清理已接**：`/config/update`、头像/封面/说说图片/相册封面/相册照片/页面背景/聊天图片在替换或删除后都会清 OSS 旧对象；只有 `UPLOADTYPE=oss` 时才执行，且只处理当前 Bucket / `oss.domain` 下、位于 `oss.dir` 前缀（默认 `images/`）的对象，外部 URL、本地 URL、历史 `mrzym.top` 图片不会被误删。删除是 best-effort，失败只打日志，不影响主流程。

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
| `mvn package` | ✅ BUILD SUCCESS，25 个测试通过（OSS 上传/删除、Referer、IP 属地、配置旧图清理、JSON 命名） |
| Spring 上下文启动 | ✅ Tomcat 起在配置端口；`mica-ip2region` 数据从 classpath 加载成功 |
| 错误响应体 | ✅ 500 + `{"code":"100007","message":"获取网站设置失败"}`（无 `result` 字段） |
| 404 响应 | ✅ `{"code":"404","message":"接口没有找到"}` |
| 未带 token | ✅ 401 + `{"code":"100016","message":"您没有权限访问，请先登录"}` |
| Node 签发的 token（4 字节密钥） | ✅ 验签通过，进入业务逻辑 |
| 过期 token | ✅ 401 + `{"code":"100016","message":"token已过期"}` |
| 伪造签名 | ✅ 403 + `{"code":"100002","message":"无效的token"}` |
| Swagger | ✅ `/v3/api-docs` 列出全部接口 |
| JSON 命名策略（`JsonNamingTest`） | ✅ 4 个用例通过：`category_name` 转下划线、`createdAt` 保持驼峰、`page`/`pageSize` 收发正常、`categoryIdList` 不被转成 `category_id_list` |
| IP 属地解析（`IpLocationServiceTest`） | ✅ `180.109.81.135`→江苏、`8.8.8.8`→美国、IPv6→广东、内网/回环→本机地址，历史文本原样返回 |
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
| `comment` 新增/回复 | ✅ 内容过敏感词、IP 落库；`from_id != author_id` / `!= to_id` 时写 `blog_notify`，文案与现网一致 |
| `comment` 前台列表 | ✅ 父级按 `parent_id IS NULL`、`new` 按时间否则按点赞；子级按 `parent_id` + 时间升序；出参含 `ipAddress`/`form_name`/`is_like`，`is_like` 按登录用户或 IP 判定 |
| `comment` 后台列表/总数 | ✅ 含 `ipAddress`，用当前用户资料覆盖 `from_avatar`/`to_avatar`/`form_name`；content/from_name/to_name/time 过滤均通过 |
| `comment` 删除/点赞 | ✅ 父评论连带删子评论、子评论只删自己；点赞/取消同步 `thumbs_up` 并刷新 `updated_at`；错误码为 100004（原实现 bug） |
| `message` 新增 | ✅ 游客自动生成「游客xxxxx」、内容过敏感词；`user_id != 1` 时给博主写 type=3 通知；返回 `发布成功` |
| `message` 列表 | ✅ 字段顺序/`avatar`/`is_like`/`comment_total` 与现网一致；支持 tag/message/time 过滤；`getAllMessage` 只带 `avatar` |
| `message` 点赞/改删 | ✅ 点赞数刷新 `updated_at`；`update`/`delete`/`backDelete` 鉴权与限流符合路由定义；热门标签按 count 倒序前 10 |
| `talk` 发布/修改 | ✅ `talkImgList` 关联整体重建；发布返回 `{id}`；修改返回 boolean；图片按关联表自增顺序输出 |
| `talk` 列表 | ✅ 后台按 status 过滤、前台只出公开；`is_top ASC, created_at DESC`；`talkImgList`/`nick_name`/`avatar`/`is_like` 缺失时不输出 |
| `talk` 详情 | ✅ 时间输出 ISO UTC（对齐现网 `{...dataValues}` 的坑），`talkImgList` 恒存在（空数组） |
| `talk` 置顶/公开/恢复/点赞/删除 | ✅ 文案按目标状态取名；回收返回 `[1]`、永久删除返回 `1`；点赞走 like_times 自增，不写 blog_like |
| `photo` 增删查 | ✅ `bulkCreate` 返回创建行数组；`type=1` 返回 `[count]`、`type=2` 返回数字；`getAllPhotosByAlbumId` 只出 status=1 且按创建时间倒序 |
| `photoAlbum` 增删改查 | ✅ 名称唯一（改时排除自己）；删除相册连带删图片；分页 `{current,size,list,total}`、全量按创建时间倒序 |
| `POST /upload/img` | ✅ multipart 字段 `file`；`oss` 模式返回 OSS 公网 URL，`local` 模式返回可直接 200 访问的 `{url}`；空文件/未配置 OSS 时 500 + `100006`；1 分钟 100 次限流 |
| `GET /` | ✅ `text/plain` 欢迎语，与原实现一致 |
| `chat` 四个接口 | ✅ `getChatList` 返回 `{current,size,total,list}`（时间 ISO UTC、size=本次条数）；`deleteOne`/`delete` 无 `result` 字段；TRUNCATE 后自增重置为 1 |
| WebSocket 8889 | ✅ Node 客户端实测：`init` -> `onlineList`（含 user_id/nick_name/avatar/createTime）；`message` -> 落库并广播带 `id`；`revert` 原样广播；`offline`/断开 -> `onlineList` 清空 |
| 全局限流 | ✅ 每个响应带 `X-RateLimit-Limit: 200` / `Remaining` / `Reset`，`/config/addView` 被接口级 100 次限流覆盖 |
| 总接口数 | ✅ 103 / 103（`endpoints.json` 全部有对应实现） |

> **踩过的坑**：`ArticleListItem` 一开始只写了 `@JsonProperty("createdAt")`，漏了
> `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`，结果输出成 `2026-10-18T19:09:39`。
> 新建任何带时间字段的 DTO 都要两个注解一起写。

> **联调用的演示数据**：`sql/seed-demo.sql`（2 篇文章、2 个分类、2 个标签、1 个用户、1 张背景图）。
> 导入命令：`mysql -uroot -p online_blog < sql/seed-demo.sql`。不导入也不影响接口，只是列表为空。

> `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 是为了绕过某个受限环境下
> Mockito 加载字节码代理失败的问题（`Failed to load ... MockMaker`）。正常开发机上可以删掉，
> 删掉后会恢复默认的 inline mock maker。

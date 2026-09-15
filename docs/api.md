# PC-Doctor API 契约 v1

> 本文档是前后端开发的共同依据。所有接口前缀 `/api`。
> 状态：初稿（2026-08-13）；2026-09-14 增补管理员改预约状态、活动写权限下放电医、站点文案接口、管理端批量操作。
> 实现中如有出入以此文档为准并在 PR 中同步修订。

## 1. 通用约定

- Base URL：开发环境 `http://localhost:8080`（前端经 vite proxy 走 `/api`）
- 认证：登录成功后服务端返回 JWT，前端请求头携带 `Authorization: Bearer <token>`
- 响应体统一为：

```json
{ "code": 0, "message": "ok", "data": { } }
```

`code = 0` 成功；非 0 为业务错误，`message` 可直接展示给用户。HTTP 状态码与业务码分离：401 = 未登录/过期，403 = 无权限，其余业务错误 HTTP 200 + 业务码。

- 分页：请求 `page`（从 1 开始）、`size`（默认 10，最大 100）；响应 `data: { "total": 42, "records": [ ... ] }`
- 时间格式：`yyyy-MM-dd HH:mm:ss`（`datetime` 序列化为字符串）
- 上传：`multipart/form-data`，单个文件最大 20MB

### 批量接口约定

管理端批量接口（§4.3 的 `/api/admin/*/batch/*`）统一入参与出参：

- 入参：`ids` 数组（**上限 200**，为空或超限返回 2001）；ids 会**去重保序**后再执行
- 出参：

```json
{ "code": 0, "message": "ok",
  "data": { "total": 10, "success": 8,
            "failures": [ { "id": "u_1", "code": 2003, "reason": "该用户存在预约/留言记录，无法删除" } ] } }
```

- **部分成功同样返回 `code: 0`** —— 失败信息在 `data.failures` 里，前端不得依赖非 0 业务码判断批量失败
- 不变式：`total` = 去重后的 id 数，恒有 `success + failures.length == total`；`failures` 恒为数组
- `failures[].code` 取自 §2 错误码表，`reason` 可直接展示给用户
- 语义：**逐条独立执行**，失败项不影响其余项（与单条接口共用同一套业务规则，不另起一套）

## 2. 错误码表

| code | 含义 |
|---|---|
| 1001 | 未登录或登录已过期（HTTP 401） |
| 1002 | 没有权限执行此操作（HTTP 403） |
| 1003 | 账号或密码错误 |
| 1004 | 该账号已被注册 |
| 1005 | 账号不存在 |
| 1006 | 密码强度不足 |
| 1007 | 请先修改初始密码 |
| 2001 | 参数校验失败 |
| 2002 | 资源不存在 |
| 2003 | 当前状态不允许此操作 |
| 3001 | 预约不存在 |
| 3002 | 只能操作自己的预约 |
| 4001 | 不支持的文件类型 |
| 4002 | 文件过大 |
| 4003 | 文件不存在 |
| 5001 | 系统繁忙 |

> 批量接口的 `failures[].code` 取自本表（会大量复用 2003「当前状态不允许此操作」，例如「该用户存在预约/留言记录，无法删除」）。

## 3. 角色

| level | 角色 | 权限范围 |
|---|---|---|
| 0 | 普通用户 | 预约、留言、个人资料 |
| 1 | 电医 | + 接单/完成、文档与**活动管理**、修改 QQ 群号 |
| 2 | 管理员 | + 用户管理、电医管理、预约管理（含**改预约状态**）、分类管理、**站点文字与二维码** |

## 4. 接口清单

### 4.1 认证 auth（公开）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录，body: `{"user_id":"admin","password":"..."}` → `data: {"token":"...","user":{...}}` |
| POST | `/api/auth/register` | 注册，body: `{"user_id","password","user_name","contact_details","sex"}` → `data: {"token":"...","user":{...}}`（注册即登录） |
| GET | `/api/auth/me` | 当前用户信息（登录） |

用户对象（所有接口统一，不含敏感字段）：

```json
{
  "userId": "admin",
  "userName": "管理员",
  "sex": "unknown",
  "level": 2,
  "contactDetails": "qq:313521996",
  "userDescription": "简介",
  "userPicture": "/api/files/avatar/xxx.png",
  "mustChangePassword": false,
  "createTime": "2026-08-13 10:00:00"
}
```

> `userDescription`/`userPicture`/`contactDetails` 为 null 或空时省略该字段。

### 4.2 用户 users

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| PUT | `/api/users/me` | 登录 | 更新自己资料，body: `{"user_name","sex","contact_details","user_description"}` |
| PUT | `/api/users/me/password` | 登录 | 改密，body: `{"old_password","new_password"}` |
| POST | `/api/users/me/avatar` | 登录 | 头像上传（multipart `file`，jpg/png，服务端裁剪压缩）→ `data: {"url":"..."}` |
| GET | `/api/doctors` | 公开 | 电医列表（level=1），分页 |

### 4.3 管理端 admin

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/admin/users` | 管理员 | 用户列表，query: `page,size,keyword,user_id,user_name,level` |
| PUT | `/api/admin/users/{id}` | 管理员 | 更新用户（可改 level，body: `{"level":1}`） |
| PUT | `/api/admin/users/{id}/level` | 管理员 | 改角色专用，body: `{"level":1}` |
| POST | `/api/admin/users/{id}/reset-password` | 管理员 | 重置为随机密码 → `data: {"new_password":"..."}`，用户下次登录强制改密 |
| DELETE | `/api/admin/users/{id}` | 管理员 | 删除用户 |
| GET | `/api/admin/doctors` | 管理员 | 电医列表（含接单统计），query: `page,size` |
| PUT | `/api/admin/appointments/{id}/status` | 管理员 | 改预约状态，body: `{"status":0\|1\|2,"doctor_id":"可选"}` |
| POST | `/api/admin/users/batch/delete` | 管理员 | 批量删除用户，body: `{"ids":["u1","u2"]}` |
| POST | `/api/admin/users/batch/level` | 管理员 | 批量改等级，body: `{"ids":["u1"],"level":1}` |
| POST | `/api/admin/appointments/batch/delete` | 管理员 | 批量删除预约，body: `{"ids":[1,2]}` |
| POST | `/api/admin/appointments/batch/status` | 管理员 | 批量改预约状态，body: `{"ids":[1,2],"status":1,"doctor_id":"d1"}` |
| POST | `/api/admin/categories/batch/delete` | 管理员 | 批量删除分类，body: `{"ids":[3,4]}` |
| POST | `/api/admin/reports/batch/handle` | 管理员 | 批量标记举报已处理，body: `{"ids":[1,2]}` |

> 6 个批量接口统一遵循 §1「批量接口约定」（部分成功返回 `code:0` + `failures` 清单，ids 上限 200）。
> 它们**逐条复用单条接口的业务规则**，因此失败原因与单条一致：删除自己 → 2003「不能删除自己」、
> 用户有预约/留言/举报记录 → 2003、分类下有预约 → 2003、举报已处理 → 2003。
> 这些在批量场景下不再抛错中断，而是作为 `failures` 项返回。

用户列表的两个搜索参数语义不同，别混用：

| 参数 | 语义 |
|---|---|
| `keyword` | **账号或昵称**模糊匹配（管理端搜索框用这个） |
| `user_id` / `user_name` | 分别精确到字段的模糊筛选，多个条件之间**取交集** |

> 把同一个关键词同时作为 `user_id` 和 `user_name` 传，会变成"账号和昵称都含该串"，几乎搜不出结果 ——
> 这正是 2026-09-14 修掉的管理端搜索框 bug。

**改预约状态**（电医误操作后的兜底修正入口）不是简单翻一个字段，而是**按目标状态反推**：

| 目标状态 | doctor_id | appointment_time | done_time |
|---|---|---|---|
| 0 排队中 | 清空 | 清空 | 清空 |
| 1 处理中 | 必填（不传则沿用现有的，仍为空 → 2001） | 电医未变则保留，换人才写当前时间 | 清空 |
| 2 已完成 | 可选（用于补登记接单电医） | 保留 | 已有则保留，无则写当前时间 |

> - `doctor_id` 必须是 level=1 的账号，否则 2001（避免造出电医工作台查不到的僵尸单）
> - 并发保护：读取后被他人变更（如电医刚接单）→ 2003，前端提示刷新重试
> - 副作用：电医的接单/完成统计实时取自预约表，退回状态会同步影响其计数
>
> **批量改状态**（`POST /api/admin/appointments/batch/status`）额外注意：
> - `doctor_id` 作用于**整批**；`status=1` 且不传 `doctor_id` 时逐条沿用各自原有电医，
>   排队中的单（原本没有电医）会以 2001「改为处理中必须指定接单电医」记入失败清单
> - `status=2` **不会**自动补电医：把排队中的单直接标成已完成，会得到没有接单电医的已完成单
>   （电医工作台与接单统计按 `doctor_id` 聚合，看不到它）

### 4.4 预约 appointments

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/appointments` | 公开 | 预约列表，query: `page,size,status,user_id,doctor_id,mine=1`（mine 需登录）；公开查询返回脱敏字段（不含联系方式） |
| GET | `/api/appointments/{id}` | 公开 | 预约详情（含发单用户与电医信息、消息列表） |
| POST | `/api/appointments` | 登录 | 提交预约，body: `{"problem_description","category_id","problem_picture","available_time","appointment_location"}` |
| PUT | `/api/appointments/{id}` | 登录 | 更新预约。本人可改自己未接单的预约；电医仅可改自己接的单 |
| DELETE | `/api/appointments/{id}` | 登录 | 撤销/删除。本人（未接单）/ 电医（自己接的单）/ 管理员 |
| POST | `/api/appointments/{id}/accept` | 电医 | 接单：status 0→1，写 appointment_time、doctor_id |
| POST | `/api/appointments/{id}/complete` | 电医 | 完成：status 1→2，写 done_time（仅接单者本人） |

预约对象：

```json
{
  "id": 1,
  "userId": "mdd3135",
  "userName": "Mao",
  "problemDescription": "电脑无法开机",
  "categoryId": 1,
  "categoryName": "系统问题",
  "problemPicture": "/api/files/appointment/xxx.jpg",
  "availableTime": "周三下午",
  "appointmentLocation": "信息楼一楼大厅",
  "status": 0,
  "doctorId": null,
  "doctorName": null,
  "createTime": "2026-08-13 10:00:00",
  "appointmentTime": null,
  "doneTime": null
}
```

status 语义：`0` 排队中 / `1` 处理中 / `2` 已完成。
状态机：0 →(accept)→ 1 →(complete)→ 2；0 可被本人 DELETE；1、2 不可撤销。

> 管理员不受上述状态机限制，可经 §4.3 的 `PUT /api/admin/appointments/{id}/status` 越级调整
> （退回排队会清空接单电医与两个时间戳，改回已完成会补完成时间）。

### 4.5 预约留言 messages

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/appointments/{id}/messages` | 公开 | 该预约的留言列表 |
| POST | `/api/appointments/{id}/messages` | 登录 | 发留言，body: `{"message":"...","picture":"可选图url"}` |
| DELETE | `/api/messages/{messageId}` | 登录 | 删除留言：本人可删自己的；电医/管理员可删任意（处置违法内容，走查建议 #3）；连带删除留言图片 |

留言对象：`{"id","appointmentId","userId","userName","message","picture","time"}`

### 4.6 文档 documents

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/documents` | 公开 | 文档列表，query: `page,size,title`（模糊搜索） |
| GET | `/api/documents/{id}` | 公开 | 文档详情 |
| POST | `/api/documents` | 电医+ | 新建，multipart 或 json：`{"title","summary","file"}`（2026-08-14 走查建议：电医可编辑文档） |
| PUT | `/api/documents/{id}` | 电医+ | 更新 |
| DELETE | `/api/documents/{id}` | 电医+ | 删除（连带删除文件） |

### 4.7 活动 activities

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/activities` | 公开 | 活动列表，query: `page,size` |
| GET | `/api/activities/{id}` | 公开 | 活动详情 |
| POST | `/api/activities` | 电医+ | 新建：`{"title","summary","file"}`（file=详情文件 url，必填；cover=封面 url，可选） |
| PUT | `/api/activities/{id}` | 电医+ | 更新（title/summary/file 均为必填，需原样回传） |
| DELETE | `/api/activities/{id}` | 电医+ | 删除 |

> 活动写权限与文档模块一致（电医可自行发布/维护活动）；无所有权概念，任一电医可改任一活动。

### 4.8 故障分类 categories

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/categories` | 公开 | 分类列表：`{"id","name"}` |
| POST | `/api/categories` | 管理员 | 新建，body: `{"name":"系统问题"}` |
| DELETE | `/api/categories/{id}` | 管理员 | 删除（按 id）；分类下有预约时 2003 硬拒绝（不级联） |
| POST | `/api/admin/categories/batch/delete` | 管理员 | 批量删除（见 §4.3），被引用的分类以 2003 记入失败清单 |

### 4.9 配置 config

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/config/qq-group` | 公开 | QQ 群号 → `data: {"value":"636956662"}` |
| PUT | `/api/config/qq-group` | 电医+ | 更新，body: `{"value":"..."}` |
| GET | `/api/config/site-content` | 公开 | 站点文案（关于我们 / 使用帮助 FAQ / 交流群） |
| PUT | `/api/config/site-content` | 管理员 | 整体更新站点文案 |

站点文案对象：

```json
{
  "aboutTitle": "关于我们",
  "aboutBody": "Markdown 正文",
  "faqItems": [{ "question": "如何提交预约？", "answer": "Markdown 答案" }],
  "qqNotice": "欢迎加入电脑医院交流群：**{qq_group}**",
  "qqQrcode": "/api/files/notice/xxx.png",
  "footerHtml": "<a href=\"https://icp.gov.moe/?keyword=20261911\" target=\"_blank\">萌ICP备20261911号</a>",
  "icpHtml": "<a href=\"https://beian.miit.gov.cn/\" target=\"_blank\">浙ICP备XXXXXXXX号</a>"
}
```

> - `aboutBody` / `faqItems[].answer` / `qqNotice` 支持 Markdown，前端渲染前经 DOMPurify 消毒
> - `{qq_group}` 占位符在渲染时替换为真实群号，避免群号变更后文案里留下死值
> - PUT **三态语义**：字段不传 = 保留原值，传空串 = 清空（`qq_qrcode` 清空会连带删除旧文件），传值 = 替换；`faq_items` 传 `[]` 表示清空全部问答
> - 长度上限：`about_title` 100、`about_body` 5000、`qq_notice` 500、`qq_qrcode` 500、`footer_html` / `icp_html` 各 1000、`faq_items` ≤ 15 条，每条 question 100 / answer 1000
> - 缺失的 key 一律回落为空串（不会因某段文案没配就把整页打成错误）
>
> **页脚区域的两个 HTML 字段**（服务端只校验长度，不做 HTML 解析）：
>
> | 字段 | 渲染位置 | 说明 |
> |---|---|---|
> | `icp_html` | 页脚**上方独立一行**（全宽） | 备案号。单独一个字段是为了能只改备案号而不动页脚 |
> | `footer_html` | 页脚信息**同一行**（站名 / 交流群之后） | 自定义页脚内容 |
>
> - 两者**互不影响**：只传其中一个，另一个原样保留（见 `SiteContentIntegrationTest#icpAndFooterAreIndependent`）
> - 都是粘贴进来的 HTML **片段**，前端按白名单（`a` `span` `br` `b` `strong` `i` `em` `p` + `href` `target` `rel` `title`）消毒后 `v-html` 渲染；`<script>`、事件属性（`onerror=` 等）、`javascript:` 伪协议一律剥掉且不执行
> - `target="_blank"` 的链接会自动补 `rel="noopener noreferrer"`（防 tabnabbing）
> - 两个字段都为空时页脚只剩站名与交流群，不出现空行

### 4.10 文件 files

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/files` | 登录 | 上传，multipart: `file` + `type`（avatar/appointment/doc/activity/message/notice）→ `data: {"url":"/api/files/type/name"}` |
| GET | `/api/files/{type}/{name}` | 公开 | 下载/展示（文件名白名单校验，拒绝路径穿越） |

允许类型：jpg/jpeg/png/webp/gif（图片）、md（文档）、pdf。

### 4.11 举报 reports（走查建议 #3）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/reports` | 登录 | 提交举报，body: `{"target_type":1|2,"target_id":<id>,"reason":"..."}`（1=预约 2=留言）；同一人同一目标仅一次，重复返回 2001 |
| GET | `/api/admin/reports` | 管理员 | 举报列表，query: `page,size,status`（0=待处理 1=已处理）；含目标快照 `targetSummary` |
| PUT | `/api/admin/reports/{id}/handle` | 管理员 | 标记已处理 |
| POST | `/api/admin/reports/batch/handle` | 管理员 | 批量标记已处理（见 §4.3） |

举报对象：`{"id","targetType","targetId","reason","reporterId","status","createTime","handleTime","handlerId","targetSummary"}`

> 标记已处理**非幂等**：对已处理的举报再次调用返回 2003「该举报已处理」。
> 单条调用即报错，批量调用则作为一条失败项返回、不中断整批。

**内容被删除时举报自动了结**：`DELETE /api/appointments/{id}` 与 `DELETE /api/messages/{id}` 成功后，
针对该目标的**待处理**举报会自动置为已处理（处理人 = 操作者），已处理的记录不会被覆盖。

> - 删除预约时，其下留言的举报也一并了结 —— 那些留言随预约一起被删，留着待处理只会让管理员
>   点「删除留言」时报「留言不存在」
> - 之所以不只在举报处理页做这件事：删除入口还有预约管理、批量删除、详情页撤销等，
>   放在 service 层才能覆盖全部路径

## 5. 权限矩阵速查

| 接口 | 公开 | 用户 | 电医 | 管理员 |
|---|---|---|---|---|
| login/register/doctors/categories(GET)/config(GET)/documents(GET)/activities(GET)/appointments(GET) | ✓ | ✓ | ✓ | ✓ |
| auth/me、users/me、files(POST)、appointments(POST)、messages(POST) | | ✓ | ✓ | ✓ |
| appointments/{id}/accept、complete | | | ✓ | ✓ |
| appointments/{id}(PUT) | | 本人 | 本人/自己接的单 | ✓ |
| appointments/{id}(DELETE) | | 本人(未接单) | 本人(未接单)/自己接的单 | ✓ |
| messages(DELETE) | | 自己发的 | 任意 | ✓ |
| config/qq-group(PUT) | | | ✓ | ✓ |
| config/site-content(PUT) | | | | ✓ |
| documents(写)、activities(写) | | | ✓ | ✓ |
| admin/appointments/{id}/status | | | | ✓ |
| admin/*/batch/*（6 个批量接口） | | | | ✓ |
| reports(POST) | | ✓ | ✓ | ✓ |
| admin/reports、categories(写) | | | | ✓ |

## 6. 安全要求（实现时强制）

- 所有 SQL 走 MyBatis-Plus 参数化，禁止字符串拼接
- 注册/更新接口按 DTO 白名单绑定，`level`/`id`/`create_time` 等敏感字段一律不接受客户端传值
- 密码仅服务端 bcrypt，任何日志/响应不得出现密码
- JWT 仅含 userId + level，有效期 12h（可配），密钥走环境变量
- 上传文件扩展名 + MIME 双白名单；下载文件名正则校验
- 公开预约列表脱敏：不含用户联系方式

# PC-Doctor API 契约 v1

> 本文档是前后端开发的共同依据。所有接口前缀 `/api`。
> 状态：初稿（2026-08-13），实现中如有出入以此文档为准并在 PR 中同步修订。

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

## 3. 角色

| level | 角色 | 权限范围 |
|---|---|---|
| 0 | 普通用户 | 预约、留言、个人资料 |
| 1 | 电医 | + 接单/完成、修改 QQ 群号 |
| 2 | 管理员 | + 用户管理、电医管理、预约管理、文档/活动/分类管理 |

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
| GET | `/api/admin/users` | 管理员 | 用户列表，query: `page,size,user_id,user_name,level` |
| PUT | `/api/admin/users/{id}` | 管理员 | 更新用户（可改 level，body: `{"level":1}`） |
| PUT | `/api/admin/users/{id}/level` | 管理员 | 改角色专用，body: `{"level":1}` |
| POST | `/api/admin/users/{id}/reset-password` | 管理员 | 重置为随机密码 → `data: {"new_password":"..."}`，用户下次登录强制改密 |
| DELETE | `/api/admin/users/{id}` | 管理员 | 删除用户 |
| GET | `/api/admin/doctors` | 管理员 | 电医列表（含接单统计），query: `page,size` |

### 4.4 预约 appointments

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/appointments` | 公开 | 预约列表，query: `page,size,status,user_id,mine=1`（mine 需登录）；公开查询返回脱敏字段（不含联系方式） |
| GET | `/api/appointments/{id}` | 公开 | 预约详情（含发单用户与电医信息、消息列表） |
| POST | `/api/appointments` | 登录 | 提交预约，body: `{"problem_description","category_id","problem_picture","available_time","appointment_location"}` |
| PUT | `/api/appointments/{id}` | 登录 | 更新预约。本人可改自己未接单的预约；电医仅可改自己接的单 |
| DELETE | `/api/appointments/{id}` | 登录 | 撤销/删除。本人（未接单）或管理员 |
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

### 4.5 预约留言 messages

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/appointments/{id}/messages` | 公开 | 该预约的留言列表 |
| POST | `/api/appointments/{id}/messages` | 登录 | 发留言，body: `{"message":"...","picture":"可选图url"}` |

留言对象：`{"id","appointmentId","userId","userName","message","picture","time"}`

### 4.6 文档 documents

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/documents` | 公开 | 文档列表，query: `page,size,title`（模糊搜索） |
| GET | `/api/documents/{id}` | 公开 | 文档详情 |
| POST | `/api/documents` | 管理员 | 新建，multipart 或 json：`{"title","summary","file"}` |
| PUT | `/api/documents/{id}` | 管理员 | 更新 |
| DELETE | `/api/documents/{id}` | 管理员 | 删除（连带删除文件） |

### 4.7 活动 activities

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/activities` | 公开 | 活动列表，query: `page,size` |
| GET | `/api/activities/{id}` | 公开 | 活动详情 |
| POST | `/api/activities` | 管理员 | 新建：`{"title","summary","cover","file"}` |
| PUT | `/api/activities/{id}` | 管理员 | 更新 |
| DELETE | `/api/activities/{id}` | 管理员 | 删除 |

### 4.8 故障分类 categories

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/categories` | 公开 | 分类列表：`{"id","name"}` |
| POST | `/api/categories` | 管理员 | 新建，body: `{"name":"系统问题"}` |
| DELETE | `/api/categories/{id}` | 管理员 | 删除（按 id） |

### 4.9 配置 config

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/config/qq-group` | 公开 | QQ 群号 → `data: {"value":"636956662"}` |
| PUT | `/api/config/qq-group` | 电医+ | 更新，body: `{"value":"..."}` |

### 4.10 文件 files

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/files` | 登录 | 上传，multipart: `file` + `type`（avatar/appointment/doc/activity/message）→ `data: {"url":"/api/files/type/name"}` |
| GET | `/api/files/{type}/{name}` | 公开 | 下载/展示（文件名白名单校验，拒绝路径穿越） |

允许类型：jpg/jpeg/png/webp/gif（图片）、md（文档）、pdf。

## 5. 权限矩阵速查

| 接口 | 公开 | 用户 | 电医 | 管理员 |
|---|---|---|---|---|
| login/register/doctors/categories(GET)/config(GET)/documents(GET)/activities(GET)/appointments(GET) | ✓ | ✓ | ✓ | ✓ |
| auth/me、users/me、files(POST)、appointments(POST)、messages(POST) | | ✓ | ✓ | ✓ |
| appointments/{id}/accept、complete | | | ✓ | ✓ |
| appointments/{id}(PUT/DELETE) | | 本人 | 本人/自己接的单 | ✓ |
| config/qq-group(PUT) | | | ✓ | ✓ |
| admin/**、documents/activities/categories(写) | | | | ✓ |

## 6. 安全要求（实现时强制）

- 所有 SQL 走 MyBatis-Plus 参数化，禁止字符串拼接
- 注册/更新接口按 DTO 白名单绑定，`level`/`id`/`create_time` 等敏感字段一律不接受客户端传值
- 密码仅服务端 bcrypt，任何日志/响应不得出现密码
- JWT 仅含 userId + level，有效期 12h（可配），密钥走环境变量
- 上传文件扩展名 + MIME 双白名单；下载文件名正则校验
- 公开预约列表脱敏：不含用户联系方式

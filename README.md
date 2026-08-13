# PC-Doctor Backend

浙江工商大学电脑医院预约系统 —— 后端（前后端分离重构版）。

旧项目（`zjgsu-pc-doctor`，2022 年）存在 SQL 注入、注册提权、明文凭据等安全与工程问题。本仓库为全新重写的后端，功能对齐旧版、不迁移旧代码。重构依据见根目录《项目分析报告.md》，任务清单见根目录 `TODO.md`。

## 技术栈

| 项 | 选型 |
| --- | --- |
| 框架 | Spring Boot 4.1.0 / Java 21 |
| ORM | MyBatis-Plus 3.5.17（含 jsqlparser 分页模块） |
| 认证 | Spring Security + JWT（jjwt 0.13.0），密码 bcrypt |
| 数据库 | MySQL 8 + Flyway 版本化迁移 |
| 其他 | Lombok、Validation、SLF4J |

## 快速开始

### 环境要求

- JDK 21
- MySQL 8（本地建库 `pc_doctor`，用户 `pcdoctor` 可按需自建）

### 配置

1. 复制 `.env.example` 为 `.env`，填入真实值（`.env` 已被 gitignore，**绝不允许提交**）：

   ```bash
   cp .env.example .env
   ```

2. `JWT_SECRET` 必须为至少 32 字节的随机串，生成方式：

   ```bash
   python -c "import secrets; print(secrets.token_hex(32))"
   ```

### 启动

```bash
./mvnw spring-boot:run   # Windows: ./mvnw.cmd spring-boot:run
```

默认 dev profile，端口 8080；启动时 Flyway 自动执行 `db/migration` 下的迁移脚本。

## 环境变量

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 连接 | `127.0.0.1` / `3306` / `pc_doctor` / `pcdoctor` / 无 |
| `JWT_SECRET` | JWT 签名密钥（≥32 字节随机串，必填） | 无 |
| `JWT_EXPIRATION_MINUTES` | JWT 有效期（分钟） | `720` |
| `PC_UPLOAD_PATH` | 上传文件存储目录 | `./upload/` |
| `CORS_ALLOWED_ORIGINS` | CORS 白名单，逗号分隔（不开放 `*`） | `http://localhost:5173,http://127.0.0.1:5173` |
| `SPRING_PROFILES_ACTIVE` | 激活 profile（dev/prod） | `dev` |
| `SERVER_PORT` | 服务端口 | `8080` |

## 项目结构

```
src/main/java/com/pcdoctor/
├── common/      # Result<T> 统一返回体、ErrorCode 业务错误码
├── config/      # CORS、MyBatis-Plus 等配置
├── exception/   # BizException + @RestControllerAdvice 全局异常处理
├── controller/ service/ mapper/ entity/ dto/   # 业务分层，随阶段 3-4 落地
src/main/resources/
├── application.yml / application-dev.yml / application-prod.yml
└── db/migration/   # Flyway 迁移脚本
```

## 文档

- **API 契约**：[docs/api.md](docs/api.md) —— RESTful 路径 + 权限矩阵 + 错误码表 + 安全要求，前后端以它为准
- 任务清单：仓库外根目录 `TODO.md`（阶段 0-9 + 安全验收清单）

## 开发状态

进行中。当前为阶段 1 交付的脚手架（统一返回体 / 错误码 / 异常处理 / 多环境配置 / API 契约），业务接口尚未实现。阶段 2 起将落地 Flyway 建表与认证授权。

## 安全约定

- 所有凭据走环境变量，仓库不含任何明文凭据
- SQL 全参数化（MyBatis-Plus），无字符串拼接
- 注册/更新字段白名单，`level` 等敏感字段不接受客户端传值
- 上传白名单、下载防路径穿越、CORS 仅白名单域名
- 上线前逐项过根目录 `TODO.md` 的安全验收清单
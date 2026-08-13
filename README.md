# PC-Doctor Backend

[![CI](https://github.com/dwl-china/pc-doctor-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/dwl-china/pc-doctor-backend/actions/workflows/ci.yml)

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
├── controller/ service/ mapper/ entity/ dto/   # 业务分层（用户/预约/留言/文档/活动/分类/配置/文件）
├── security/   # JwtUtil + JwtAuthenticationFilter
└── db/migration/   # Flyway 迁移（V3 管理员种子为 Java 迁移）
src/main/resources/
├── application.yml / application-dev.yml / application-prod.yml
└── db/migration/   # Flyway 迁移脚本
```

## 文档

- **API 契约**：[docs/api.md](docs/api.md) —— RESTful 路径 + 权限矩阵 + 错误码表 + 安全要求，前后端以它为准
- 任务清单：仓库外根目录 `TODO.md`（阶段 0-9 + 安全验收清单）

## 测试

```bash
./mvnw test    # 全部测试（单元 + MockMvc 集成 + 安全回归，共 55 用例）
```

- 单元测试：预约状态机/权限规则、密码逻辑、文件白名单与路径穿越（Mockito + 临时目录）
- 集成测试：test profile + `@Transactional` 回滚（本地复用开发库不污染数据；CI 用 MySQL 服务容器全新库）
- 安全回归：SQL 注入（登录/搜索/筛选）、注册提权、越权、路径穿越
- CI：GitHub Actions（push/PR 触发，JDK 21 + MySQL 8 + `./mvnw verify`）

## 开发状态

阶段 0-5 已完成（2026-08-13）：Flyway 建表与种子数据、Security + JWT 认证授权、全部业务模块接口、测试与 CI。剩余：阶段 6-7 前端、阶段 8 联调部署。

## 安全约定

- 所有凭据走环境变量，仓库不含任何明文凭据
- SQL 全参数化（MyBatis-Plus），无字符串拼接
- 注册/更新字段白名单，`level` 等敏感字段不接受客户端传值
- 上传白名单、下载防路径穿越、CORS 仅白名单域名
- 上线前逐项过根目录 `TODO.md` 的安全验收清单
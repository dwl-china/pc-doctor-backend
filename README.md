# PC-Doctor Backend

[![CI](https://github.com/dwl-china/pc-doctor-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/dwl-china/pc-doctor-backend/actions/workflows/ci.yml)

电脑医院预约系统 —— 后端（重构版）。

旧项目（[zjgsu-pc-doctor](https://github.com/mdd3135/zjgsu-pc-doctor)，2022 年）存在 SQL 注入、注册提权、明文凭据等安全与工程问题。本仓库为全新重写的后端，功能对齐旧版、不迁移旧代码。

## 关于本项目

本项目来自**浙江工商大学**兴趣社团X计算机协会**电脑医院**

**X计算机协会**成立于1995年，是一个面向全校、以营造校园科技文化氛围，推广计算机应用操作为宗旨的学术科技类社团。

**电脑医院**隶属于浙江工商大学计算机协会，旨在为在校师生提供免费、专业、便捷的IT技术支援活动

本项目为公益项目，是电脑医院内部自行组织、开发的系统。

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
| `ADMIN_USERNAME` / `ADMIN_DISPLAY_NAME` | Flyway 种子管理员账号/昵称（仅首次建库生效） | `admin` / `管理员` |
| `ADMIN_INITIAL_PASSWORD` | 种子管理员初始密码（仅首次建库生效，迁移时 bcrypt 入库；**上线后请立即登录改密**） | 无 |

> `.env.example` 为全量模板；生产环境用 `.env` 注入真实值，`SPRING_PROFILES_ACTIVE=prod` 可启用 `application-prod.yml` 覆盖。

## 项目结构

```text
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
- **老库迁移**：[docs/migration.sql](docs/migration.sql)（数据迁移脚本）+ [docs/migration-dryrun.md](docs/migration-dryrun.md)（演练手册）
- 任务清单：仓库外根目录 `TODO.md`（阶段 0-9 + 安全验收清单）

## 生产部署

### 1. 打包

```bash
./mvnw package            # 含全部测试（56 用例）
./mvnw package -DskipTests # 跳过测试
```

产物：`target/pc-doctor-backend-0.0.1-SNAPSHOT.jar`。

### 2. 准备运行环境

- JDK 21；MySQL 8 建库并授权（`pc_doctor`，utf8mb4）
- 上传目录：`PC_UPLOAD_PATH` 指向的目录需存在且对运行用户可写
- `.env` 与 jar 同目录（`spring.config.import` 会加载），或改用系统环境变量注入

### 3. 启动

```bash
java -jar pc-doctor-backend-0.0.1-SNAPSHOT.jar
# 指定 profile：SPRING_PROFILES_ACTIVE=prod java -jar ...
```

- 首次启动 Flyway 自动建表 + 种子（admin 密码来自 `ADMIN_INITIAL_PASSWORD`）
- 建议配 systemd（Linux）或 NSSM/任务计划（Windows）守护，`Restart=always`
- **上线首日**：登录 admin 改密；若从老库迁移，先按 migration-dryrun.md 演练

### 4. 前端与反向代理

前端 `vite build` 产物由 Nginx 托管，`/api/` 反代到本服务 8080。完整配置（HTTPS、SPA 回退、上传大小限制）见仓库外根目录 `deploy/nginx.conf`。

### 5. 上线前检查

- 逐项核对根目录 `TODO.md` 的安全验收清单（SQL 注入/白名单/密码/凭据/路径/会话/越权/CORS）
- `CORS_ALLOWED_ORIGINS` 改为生产域名（拒绝 `*`）
- 若从老库迁移：migration-dryrun.md 演练通过后再动正式库

## 测试

```bash
./mvnw test    # 全部测试（单元 + MockMvc 集成 + 安全回归，共 56 用例）
```

- 单元测试：预约状态机/权限规则、密码逻辑、文件白名单与路径穿越（Mockito + 临时目录）
- 集成测试：test profile + `@Transactional` 回滚（本地复用开发库不污染数据；CI 用 MySQL 服务容器全新库）
- 安全回归：SQL 注入（登录/搜索/筛选）、注册提权、越权、路径穿越
- CI：GitHub Actions（push/PR 触发，JDK 21 + MySQL 8 + `./mvnw verify`）

## 开发状态

- 阶段 0-5 已完成（2026-08-13）：Flyway 建表与种子数据、Security + JWT 认证授权、全部业务模块接口、测试与 CI。
- 阶段 8（2026-08-14）：前后端联调按契约逐接口核对通过；修复 4 处契约出入（错误码语义/密码强度 1006/分页钳制/JSON 解析 2001）；生产 jar 冒烟通过；走查反馈 6 条全部落地（留言删除修复、电医文档权限、举报体系 V4、首页科普文档、全站字数限制、预约图片脱敏）；老数据迁移演练通过；安全验收八项全勾。
- 剩余：正式部署 + 上线后观察（TODO 10.3）；QQ 机器人通知（TODO 5.9，暂缓项）。

## 安全约定

- 所有凭据走环境变量，仓库不含任何明文凭据
- SQL 全参数化（MyBatis-Plus），无字符串拼接
- 注册/更新字段白名单，`level` 等敏感字段不接受客户端传值
- 上传白名单、下载防路径穿越、CORS 仅白名单域名
- 上线前逐项过根目录 `TODO.md` 的安全验收清单

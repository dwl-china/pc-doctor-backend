# 老数据迁移演练手册（dry-run）

> 目标：在**演练库**上完整跑一遍迁移（还原备份 → Flyway 建新库 → 执行 migration.sql → 校验），
> 证明脚本可用后再在正式环境执行。全程不碰生产库 `pc_doctor`。
>
> 演练库：`demo`（老库，还原备份）+ `pc_doctor_dryrun`（新库，Flyway 迁移）。
>
> **2026-08-14 已演练通过**：迁移结果 users 6→4、appointments 5→4、分类同名直插跳过、
> 迁移用户密码置空 + must_change_password=1、群号保留种子值。踩坑记录见文末。

## 步骤 0：准备（用户执行，需 MySQL root）

在 MySQL 客户端执行：

```sql
-- 1) 建演练库并授权给应用账号 pcdoctor（Flyway/迁移脚本用）
CREATE DATABASE IF NOT EXISTS pc_doctor_dryrun CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON pc_doctor_dryrun.* TO 'pcdoctor'@'localhost';
-- 2) 迁移脚本要读老库 demo（只读即可）
GRANT SELECT ON demo.* TO 'pcdoctor'@'localhost';
FLUSH PRIVILEGES;

-- 3) 还原老库 demo（备份文件含 CREATE DATABASE demo）
--    注意：备份第 17 行混入了一行 mysqldump 的 stderr 文本（非 SQL），
--    先删掉再导入，否则 mysql 批处理遇语法错误会中止
```

命令行（Git Bash）：

```bash
sed '17d' "D:/pc_doctor/backup/demo_20260813_121456.sql" > /tmp/demo_clean.sql
"/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -u root -p < /tmp/demo_clean.sql
```

确认 `demo` 库出现 7 张表且 user_table 有 6 行。

## 步骤 1：Flyway 迁移演练库（Claude 执行）

用临时环境变量覆盖库名，启动后端让 Flyway 建表 + 种子（端口换 8081 避开 dev 实例）：

```bash
DB_NAME=pc_doctor_dryrun SERVER_PORT=8081 ./mvnw spring-boot:run
# 日志出现 "Successfully applied 4 migrations" 后停掉（taskkill 按 8081 端口的 java 进程）
```

## 步骤 2：执行迁移脚本（Claude 执行）

**注意**：sed 必须**同时**替换 `USE` 行与所有 `pc_doctor.` 库名前缀（只换 USE 行会把数据写进 dev 库！），
并加 `--default-character-set=utf8mb4`（Windows mysql 客户端默认 GBK 读文件，中文字面量会 collation 冲突）。
**不要用管道**（Git Bash 管道下 mysql.exe 可能不退出）；写文件后重定向：

```bash
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
sed 's/USE pc_doctor;/USE pc_doctor_dryrun;/; s/pc_doctor\./pc_doctor_dryrun./g' \
    docs/migration.sql > /tmp/migration_dryrun.sql
"$MYSQL" -h 127.0.0.1 -u pcdoctor -p --default-character-set=utf8mb4 < /tmp/migration_dryrun.sql
```

## 步骤 3：校验（脚本末尾自带对比查询，另附人工核对点）

| 核对项 | 预期 |
|---|---|
| 用户数 | 老库 6 → 新库 4（剔除 `2012190330`、`admin123`；`admin` 与种子重合不重复插入） |
| 预约数 | 老库 5 → 新库 4（admin 2 条 + mdd3135 2 条；admin123 的 1 条随用户剔除） |
| 预约分类 | '网络'/'系统' 自由文本 → 兜底「系统问题」(id=1) |
| 迁移用户 | 全部 must_change_password=1、password_hash 为空 |
| 分类/群号 | 分类 5 条同名直插跳过；群号保留种子值 636956662 |

## 演练完成后清理

```sql
DROP DATABASE IF EXISTS demo;
DROP DATABASE IF EXISTS pc_doctor_dryrun;
```

## 踩坑记录（2026-08-14 演练实况）

1. **sed 只替换 `USE` 行 → 数据写进 dev 库**：migration.sql 内所有 `pc_doctor.user_table` 等引用
   必须一并替换为演练库前缀。当时发现后已从 dev 库精确清除误插数据（3 用户 + 12 预约）。
2. **字符集**：Windows mysql 客户端默认按 GBK 读输入，SQL 内中文字面量与 utf8mb4 列比较报
   `Illegal mix of collations` → 必须 `--default-character-set=utf8mb4`。
3. **Git Bash 管道喂 stdin**：`sed ... | mysql` 下 mysql.exe 偶发不退出、连接挂起 → 写文件再 `<` 重定向。
4. **kill 后台 Java 前先看进程名**：8081/8080 的 Java 进程同时会以客户端身份连 3306，
   按端口 LISTEN 定位再 taskkill，避免误杀 dev 后端。

-- V2__seed.sql
-- 种子数据：问题分类 + 键值配置。
-- 管理员账号不在此文件（明文/哈希都不入库脚本），由 V3__seed_admin.java
-- 在迁移时读取 ADMIN_INITIAL_PASSWORD 环境变量、bcrypt 后插入。

INSERT INTO category_table (name)
VALUES ('系统问题'),
       ('电脑清灰'),
       ('病毒扫描'),
       ('流氓软件清理'),
       ('重装系统');

-- QQ 群号（沿用老系统群号，管理端可改）
INSERT INTO config_table (config_key, config_value)
VALUES ('qq_group', '636956662');
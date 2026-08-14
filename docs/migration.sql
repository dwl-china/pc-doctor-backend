-- ============================================================================
-- migration.sql —— 老库(demo) → 新库(pc_doctor) 数据迁移脚本
-- ============================================================================
-- 前提：
--   1. 老库已还原为 `demo`（直接执行备份文件 demo_20260813_121456.sql 即可，
--      备份内含 CREATE DATABASE demo + 全部表数据）
--   2. 新库已由 Flyway 完成迁移（V1 建表 + V2 种子），即 `pc_doctor` 库就绪
--   3. 用具备两个库读写权限的账号执行（如 root 或对 demo 有 SELECT 的账号）
--
-- 安全说明：
--   * 老库密码是 MD5（不可逆），无法转成 bcrypt —— 所有迁移用户
--     password_hash 置空 + must_change_password=1，上线后由管理员
--     「重置密码」发放随机密码（见 TODO 4.7），用户首次登录强制改密
--   * 老库 session_id/expiration_time 为会话残留，JWT 无状态方案下不迁移
--   * 按 TODO 3.2 要求剔除弱账号：空密码电医 `2012190330`、冗余管理员 `admin123`
--   * 分类名映射：老库自由文本 → 新库 category_table.id（见下方 CASE）
-- ============================================================================

USE pc_doctor;

-- ----------------------------------------------------------------------------
-- 1. 用户表（剔除弱账号；MD5 密码置空并标记需重置）
-- ----------------------------------------------------------------------------
INSERT INTO pc_doctor.user_table
    (user_id, user_name, sex, password_hash, level, must_change_password,
     contact_details, user_description, user_picture, create_time, update_time)
SELECT u.user_id,
       NULLIF(u.user_name, ''),                              -- 空昵称置 NULL 由前端兜底
       u.sex,
       ''                                                    AS password_hash,  -- 不可逆，强制重置
       CAST(u.level AS UNSIGNED),
       1                                                     AS must_change_password,
       u.contact_details,
       u.user_description,
       u.user_picture,
       NOW()                                                 AS create_time,     -- 老库无注册时间
       NOW()
FROM demo.user_table u
WHERE u.user_id NOT IN ('2012190330', 'admin123')            -- TODO 3.2 弱账号剔除
  AND NOT EXISTS (SELECT 1 FROM pc_doctor.user_table n WHERE n.user_id = u.user_id); -- 不覆盖种子管理员

-- ----------------------------------------------------------------------------
-- 2. 分类表：老库分类名 → 新库 id
--    2026-08-13 备份实况：老库分类表 5 条与种子完全同名 → INSERT IGNORE 全部跳过。
--    预约表的 problem_category 是自由文本（'系统'/'网络' 等），不保证落在分类表内，
--    同名匹配不到时兜底到「系统问题」（见步骤 3）。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO pc_doctor.category_table (name)
SELECT category FROM demo.category_table;

-- ----------------------------------------------------------------------------
-- 3. 预约表（status 由字符串转 tinyint；doctor_id 老库实际全空，NULL 保留）
-- ----------------------------------------------------------------------------
INSERT INTO pc_doctor.appointment_table
    (user_id, problem_description, category_id, problem_picture, available_time,
     appointment_location, status, doctor_id, create_time, appointment_time, done_time)
SELECT a.user_id,
       a.problem_description,
       COALESCE(c.id, fallback.id),
       NULLIF(a.problem_picture, ''),
       NULLIF(a.available_time, ''),
       a.appointment_location,
       CAST(a.status AS UNSIGNED),
       NULLIF(a.doctor_id, ''),
       a.create_time,
       a.appointment_time,
       a.done_time
FROM demo.appointment_table a
LEFT JOIN demo.category_table oc ON oc.category = a.problem_category          -- 同名分类直连
LEFT JOIN pc_doctor.category_table c ON c.name = oc.category                  -- 映射到新库 id
LEFT JOIN pc_doctor.category_table fallback ON fallback.name = '系统问题'      -- 未命中兜底
WHERE EXISTS (SELECT 1 FROM pc_doctor.user_table n WHERE n.user_id = a.user_id); -- 仅迁移存活用户

-- ----------------------------------------------------------------------------
-- 4. 留言表（老库 user_name 实为登录账号 → 对齐新库 user_id；time → create_time）
--    注意：2026-08-13 备份中该表为空，语句保留以备老库后续有数据
-- ----------------------------------------------------------------------------
INSERT INTO pc_doctor.message_table (appointment_id, user_id, message, picture, create_time)
SELECT m.appointment_id,
       m.user_name,
       m.message,
       m.picture,
       m.time
FROM demo.message_table m
WHERE EXISTS (SELECT 1 FROM pc_doctor.appointment_table n WHERE n.id = m.appointment_id)
  AND EXISTS (SELECT 1 FROM pc_doctor.user_table u WHERE u.user_id = m.user_name);

-- ----------------------------------------------------------------------------
-- 5. 文档表 / 活动表（备份中为空表，语句保留以备老库后续有数据）
-- ----------------------------------------------------------------------------
INSERT INTO pc_doctor.document_table (title, summary, file, create_time)
SELECT title, summary, file, create_time FROM demo.document_table;

INSERT INTO pc_doctor.activity_table (title, summary, file, cover, create_time)
SELECT title, summary, file, cover, create_time FROM demo.activity_table;

-- ----------------------------------------------------------------------------
-- 6. notice_table → config_table（QQ 群号）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO pc_doctor.config_table (config_key, config_value)
SELECT 'qq_group', id FROM demo.notice_table LIMIT 1;

-- ----------------------------------------------------------------------------
-- 7. 校验（迁移后人工核对）
-- ----------------------------------------------------------------------------
-- 用户数应 = 老库用户数 - 2（剔除 2012190330、admin123，种子 admin 不重复计数时）
SELECT (SELECT COUNT(*) FROM demo.user_table)        AS old_users,
       (SELECT COUNT(*) FROM pc_doctor.user_table)   AS new_users;
SELECT (SELECT COUNT(*) FROM demo.appointment_table) AS old_appointments,
       (SELECT COUNT(*) FROM pc_doctor.appointment_table) AS new_appointments;
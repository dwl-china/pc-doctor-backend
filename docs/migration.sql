-- ============================================================================
-- migration.sql —— 老库(demo) → 新库(pc_doctor) 数据迁移脚本
-- ============================================================================
-- 前提：
--   1. 老库已还原为 `demo`（导入备份文件即可，导完核对 7 张表行数）
--   2. 新库已由 Flyway 完成迁移（V1 建表 + V2 种子 + V3 管理员），即 `pc_doctor` 库就绪
--   3. 用具备两个库读写权限的账号执行（如 root 或对 demo 有 SELECT 的账号）
--
-- 安全说明：
--   * 老库密码是 MD5（不可逆），无法转成 bcrypt —— 所有迁移用户
--     password_hash 置空 + must_change_password=1，上线后由管理员
--     「重置密码」发放随机密码（见 TODO 4.7），用户首次登录强制改密
--   * 老库 session_id/expiration_time 为会话残留，JWT 无状态方案下不迁移
--   * 按 TODO 3.2 要求剔除弱账号：空密码电医 `2012190330`、冗余管理员 `admin123`
--
-- 2026-09-14 针对**真实老库**（418 用户 / 211 预约）修订，修掉 5 个坑：
--   ① 性别是老库中文值（未知/男/女）→ 新库按 male/female/unknown 枚举映射
--   ② 预约必须**保留老 id**：老 id 是稀疏值（15-282），新表自增会重排，
--      而留言按 appointment_id 关联 —— 不保留 id 会挂到错误的预约上
--   ③ 分类是老库自由文本且**支持多选**（'电脑清灰,流氓软件清理'）→ 取逗号前
--      第一段做匹配，匹配不到兜底「系统问题」（新库 category_id 是单个外键）
--   ④ 图片字段存的是裸文件名（'1662188082264.jpg'），且存在字面量 'null'
--      → 规范化为新库 URL `/api/files/{type}/{name}`，把实体文件拷进
--      PC_UPLOAD_PATH 对应目录即可直接显示；字面量 'null'/空串一律置 NULL
--   ⑤ 留言表 user_name 存的是老前端 bug 写进去的字面量 'undefined'
--      → 无法归属到任何用户，外键约束下必然被丢弃（下方步骤 4 的 EXISTS 过滤）
-- ============================================================================

USE pc_doctor;

-- ----------------------------------------------------------------------------
-- 1. 用户表（剔除弱账号；MD5 密码置空并标记需重置；性别中→英映射）
-- ----------------------------------------------------------------------------
INSERT INTO pc_doctor.user_table
    (user_id, user_name, sex, password_hash, level, must_change_password,
     contact_details, user_description, user_picture, create_time, update_time)
SELECT u.user_id,
       COALESCE(NULLIF(u.user_name, ''), u.user_id),   -- 空昵称兜底为账号，新库该列 NOT NULL
       CASE u.sex                                      -- 老库默认值就是中文「未知」
           WHEN '男' THEN 'male'
           WHEN '女' THEN 'female'
           WHEN 'male' THEN 'male'
           WHEN 'female' THEN 'female'
           ELSE 'unknown'
       END,
       ''                                              AS password_hash,   -- 不可逆，强制重置
       CAST(u.level AS UNSIGNED),
       1                                               AS must_change_password,
       u.contact_details,
       NULLIF(NULLIF(u.user_description, ''), 'null'),
       CASE                                            -- 头像：裸文件名 → 新库 URL
           WHEN u.user_picture IS NULL OR u.user_picture IN ('', 'null') THEN NULL
           WHEN u.user_picture LIKE '/api/files/%' THEN u.user_picture
           ELSE CONCAT('/api/files/avatar/', u.user_picture)
       END,
       NOW()                                           AS create_time,      -- 老库无注册时间
       NOW()
FROM demo.user_table u
WHERE u.user_id NOT IN ('2012190330', 'admin123')            -- TODO 3.2 弱账号剔除
  AND NOT EXISTS (SELECT 1 FROM pc_doctor.user_table n WHERE n.user_id = u.user_id); -- 不覆盖种子管理员

-- ----------------------------------------------------------------------------
-- 2. 分类表：老库分类名 → 新库 id
--    老库 6 条：种子 5 条同名（INSERT IGNORE 跳过）+「联网问题」补插。
--    必须先于步骤 3 执行 —— 预约映射依赖这里的 id。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO pc_doctor.category_table (name)
SELECT category FROM demo.category_table;

-- ----------------------------------------------------------------------------
-- 3. 预约表（保留老 id；分类取多选的第一段；status 字符串转 tinyint）
-- ----------------------------------------------------------------------------
INSERT INTO pc_doctor.appointment_table
    (id, user_id, problem_description, category_id, problem_picture, available_time,
     appointment_location, status, doctor_id, create_time, appointment_time, done_time)
SELECT a.id,                                             -- 保留老 id，留言才挂得对
       a.user_id,
       a.problem_description,
       COALESCE(c.id, fallback.id),                      -- 精确匹配 → 兜底「系统问题」
       CASE                                              -- 预约图：裸文件名 → 新库 URL
           WHEN a.problem_picture IS NULL OR a.problem_picture IN ('', 'null') THEN NULL
           WHEN a.problem_picture LIKE '/api/files/%' THEN a.problem_picture
           ELSE CONCAT('/api/files/appointment/', a.problem_picture)
       END,
       NULLIF(NULLIF(a.available_time, ''), 'null'),
       NULLIF(NULLIF(a.appointment_location, ''), 'null'),
       CAST(a.status AS UNSIGNED),
       NULLIF(NULLIF(a.doctor_id, ''), 'null'),
       a.create_time,
       a.appointment_time,
       a.done_time
FROM demo.appointment_table a
-- 分类自由文本支持多选（逗号分隔），取第一段匹配；老库无空格，TRIM 兜底
LEFT JOIN pc_doctor.category_table c
       ON c.name = TRIM(SUBSTRING_INDEX(a.problem_category, ',', 1))
LEFT JOIN pc_doctor.category_table fallback
       ON fallback.name = '系统问题'
WHERE EXISTS (SELECT 1 FROM pc_doctor.user_table n WHERE n.user_id = a.user_id); -- 仅迁移存活用户

-- ----------------------------------------------------------------------------
-- 4. 留言表（老库 user_name 实为登录账号 → 对齐新库 user_id；time → create_time）
--    注意：真实老库 4 条留言的 user_name 全是字面量 'undefined'（老前端 bug，
--    见分析报告 #20），下方 EXISTS 会全部过滤掉 —— 这是预期行为，不是漏迁。
-- ----------------------------------------------------------------------------
INSERT INTO pc_doctor.message_table (appointment_id, user_id, message, picture, create_time)
SELECT m.appointment_id,
       m.user_name,
       m.message,
       CASE
           WHEN m.picture IS NULL OR m.picture IN ('', 'null') THEN NULL
           WHEN m.picture LIKE '/api/files/%' THEN m.picture
           ELSE CONCAT('/api/files/message/', m.picture)
       END,
       m.time
FROM demo.message_table m
WHERE EXISTS (SELECT 1 FROM pc_doctor.appointment_table n WHERE n.id = m.appointment_id)
  AND EXISTS (SELECT 1 FROM pc_doctor.user_table u WHERE u.user_id = m.user_name);

-- ----------------------------------------------------------------------------
-- 5. 文档表 / 活动表（文档 file 存裸文件名，规范化为新库 URL 以便前端预览）
-- ----------------------------------------------------------------------------
INSERT INTO pc_doctor.document_table (title, summary, file, create_time)
SELECT d.title,
       NULLIF(NULLIF(d.summary, ''), 'null'),
       CASE                                             -- 新库 file 列 NOT NULL，必须有值
           WHEN d.file IS NULL OR d.file = '' THEN CONCAT('missing-', d.id, '.md')
           WHEN d.file LIKE '/api/files/%' THEN d.file
           ELSE CONCAT('/api/files/doc/', d.file)
       END,
       d.create_time
FROM demo.document_table d;

INSERT INTO pc_doctor.activity_table (title, summary, file, cover, create_time)
SELECT a.title, a.summary, a.file, a.cover, a.create_time FROM demo.activity_table a;

-- ----------------------------------------------------------------------------
-- 6. notice_table → config_table（QQ 群号）
--    真实老库该值为**空字符串**（导出时群号已清空），故加非空守卫 →
--    保留 V2 种子里的 636956662，不被空值覆盖。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO pc_doctor.config_table (config_key, config_value)
SELECT 'qq_group', id FROM demo.notice_table
WHERE id IS NOT NULL AND id <> '' LIMIT 1;

-- ----------------------------------------------------------------------------
-- 7. 校验（迁移后人工核对；括号内为 2026-09-14 真实老库的预期值）
-- ----------------------------------------------------------------------------
SELECT (SELECT COUNT(*) FROM demo.user_table)         AS old_users,        -- 418
       (SELECT COUNT(*) FROM pc_doctor.user_table)    AS new_users;        -- 417（416 + 种子 admin）
SELECT (SELECT COUNT(*) FROM demo.appointment_table)  AS old_appointments, -- 211
       (SELECT COUNT(*) FROM pc_doctor.appointment_table) AS new_appointments; -- 211
SELECT (SELECT COUNT(*) FROM pc_doctor.document_table) AS new_documents,    -- 4
       (SELECT COUNT(*) FROM pc_doctor.category_table) AS new_categories;   -- 6
SELECT (SELECT COUNT(*) FROM pc_doctor.message_table)  AS new_messages;     -- 0（老库 4 条均无归属）

-- 抽样核对：性别映射、分类兜底、图片路径规范化是否生效
SELECT sex, COUNT(*) AS n FROM pc_doctor.user_table GROUP BY sex;           -- unknown 414 / male 4 / female 1（含种子 admin）
SELECT c.name AS category, COUNT(*) AS n
FROM pc_doctor.appointment_table a JOIN pc_doctor.category_table c ON c.id = a.category_id
GROUP BY c.name;                                                            -- 系统问题 ~36 条兜底前身
SELECT id, user_id, problem_picture FROM pc_doctor.appointment_table
WHERE problem_picture IS NOT NULL ORDER BY id LIMIT 3;                      -- 应形如 /api/files/appointment/xxx.jpg
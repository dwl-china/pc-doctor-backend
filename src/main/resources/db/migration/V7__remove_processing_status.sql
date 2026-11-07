-- ============================================================================
-- V7：预约状态简化为「待处理(0) / 已处理(2)」
--   * 接单即完成：AppointmentService.accept 0→2，不再有「处理中」
--   * 历史 status=1 的单视为已接单，直接归入已处理并补完成时间
--   * 同步 FAQ 默认文案；只在仍是旧默认文案时替换，不覆盖管理端自定义内容
-- ============================================================================

UPDATE appointment_table
SET status    = 2,
    done_time = COALESCE(done_time, NOW())
WHERE status = 1;

ALTER TABLE appointment_table
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待处理 2=已处理',
    MODIFY COLUMN appointment_time DATETIME NULL COMMENT '接单时间（接单即完成，与 done_time 同时写入）',
    MODIFY COLUMN done_time DATETIME NULL COMMENT '处理完成时间';

UPDATE config_table
SET config_value = REPLACE(
        REPLACE(
            REPLACE(config_value,
                    '提交后预约进入「排队中」，等待电医接单。',
                    '提交后预约进入「待处理」，等待电医接单。'),
            '预约状态会在 排队中 → 处理中 → 已完成 之间流转。',
            '预约状态会在 待处理 → 已处理 之间流转。'),
        '预约处于「排队中」时',
        '预约处于「待处理」时')
WHERE config_key = 'faq_items';

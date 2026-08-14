-- ============================================================================
-- V4：举报表（走查建议 #3：举报 + 电医/管理员删除违法内容）
-- 设计：
--   * 举报对象为预约(1)或留言(2)，不设外键——目标被删除后举报记录仍留档
--   * 同一人同一目标只能举报一次（唯一键），重复举报提示已受理
--   * 处置在管理端完成：删除违法内容（接口独立）+ 标记已处理
-- ============================================================================
CREATE TABLE report_table (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '举报 id',
    target_type TINYINT      NOT NULL COMMENT '1=预约 2=留言',
    target_id   BIGINT       NOT NULL COMMENT '预约 id / 留言 id',
    reason      VARCHAR(500) NOT NULL COMMENT '举报原因',
    reporter_id VARCHAR(50)  NOT NULL COMMENT '举报人账号',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0=待处理 1=已处理',
    create_time DATETIME     NOT NULL COMMENT '举报时间',
    handle_time DATETIME     NULL COMMENT '处理时间',
    handler_id  VARCHAR(50)  NULL COMMENT '处理人（管理员）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_once (reporter_id, target_type, target_id),
    KEY idx_report_status (status),
    KEY idx_report_target (target_type, target_id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES user_table (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='内容举报';

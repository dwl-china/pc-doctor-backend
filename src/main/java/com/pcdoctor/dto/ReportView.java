package com.pcdoctor.dto;

import java.time.LocalDateTime;

/**
 * 举报视图（管理端列表，契约 §4.11）。
 * targetSummary 为举报目标快照（预约问题描述 / 留言内容），目标已删除时为空。
 */
public record ReportView(
        Long id,
        Integer targetType,
        Long targetId,
        String reason,
        String reporterId,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime handleTime,
        String handlerId,
        String targetSummary) {
}

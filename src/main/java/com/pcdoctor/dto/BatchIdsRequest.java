package com.pcdoctor.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 数字 id 的批量请求（预约 / 分类 / 举报共用，契约 §4.3）。
 * 元素上的 {@code @NotNull} 防 {@code [1,null]} 传到 selectById(null)。
 */
public record BatchIdsRequest(
        @NotEmpty(message = "请选择至少一项")
        @Size(max = 200, message = "单次最多 200 项")
        List<@NotNull(message = "id 不能为空") Long> ids) {
}

package com.pcdoctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 用户账号（字符串 id）的批量请求（契约 §4.3）。
 */
public record BatchUserIdsRequest(
        @NotEmpty(message = "请选择至少一项")
        @Size(max = 200, message = "单次最多 200 项")
        List<@NotBlank(message = "账号不能为空") String> ids) {
}

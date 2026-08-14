package com.pcdoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 举报请求（契约 §4.11）。
 */
public record ReportRequest(
        @JsonProperty("target_type")
        @NotNull(message = "举报对象类型不能为空")
        @Min(value = 1, message = "举报对象类型不合法")
        @Max(value = 2, message = "举报对象类型不合法")
        Integer targetType,

        @JsonProperty("target_id")
        @NotNull(message = "举报对象不能为空")
        Long targetId,

        @NotBlank(message = "举报原因不能为空")
        @Size(max = 500, message = "举报原因最长 500 字")
        String reason) {
}

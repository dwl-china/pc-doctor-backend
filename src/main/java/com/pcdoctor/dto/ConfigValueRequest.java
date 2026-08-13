package com.pcdoctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 配置更新（契约 §4.9）。
 */
public record ConfigValueRequest(
        @NotBlank(message = "值不能为空")
        @Size(max = 500, message = "值过长")
        String value) {
}
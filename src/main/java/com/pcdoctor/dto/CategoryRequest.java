package com.pcdoctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 分类新建（契约 §4.8）。
 */
public record CategoryRequest(
        @NotBlank(message = "分类名不能为空")
        @Size(max = 50, message = "分类名最长 50 字")
        String name) {
}
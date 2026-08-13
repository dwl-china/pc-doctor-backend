package com.pcdoctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文档新建/更新（契约 §4.6）。
 */
public record DocumentRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 255, message = "标题过长")
        String title,

        @Size(max = 500, message = "摘要过长")
        String summary,

        @NotBlank(message = "文件路径不能为空")
        @Size(max = 255, message = "文件路径过长")
        String file) {
}
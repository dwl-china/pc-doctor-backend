package com.pcdoctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发留言（契约 §4.5）。
 */
public record MessageRequest(
        @NotBlank(message = "留言内容不能为空")
        @Size(max = 1000, message = "留言最长 1000 字")
        String message,

        @Size(max = 500, message = "图片路径过长")
        String picture) {
}
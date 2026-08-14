package com.pcdoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求（契约 §4.1）。
 */
public record LoginRequest(
        @JsonProperty("user_id")
        @NotBlank(message = "账号不能为空")
        @Size(max = 50, message = "账号最长 50 位")
        String userId,

        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码最长 64 位")
        String password) {
}
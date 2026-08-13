package com.pcdoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求（契约 §4.1）。
 */
public record LoginRequest(
        @JsonProperty("user_id")
        @NotBlank(message = "账号不能为空")
        String userId,

        @NotBlank(message = "密码不能为空")
        String password) {
}
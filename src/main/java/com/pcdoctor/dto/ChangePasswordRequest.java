package com.pcdoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 改密请求（契约 §4.2）。
 */
public record ChangePasswordRequest(
        @JsonProperty("old_password")
        @NotBlank(message = "旧密码不能为空")
        @Size(max = 64, message = "旧密码最长 64 位")
        String oldPassword,

        @JsonProperty("new_password")
        @NotBlank(message = "新密码不能为空")
        @Size(max = 64, message = "新密码最长 64 位")
        String newPassword) {
}
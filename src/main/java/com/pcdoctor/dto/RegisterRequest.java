package com.pcdoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求（契约 §4.1）。
 * 白名单 DTO：仅接受这 5 个字段，level 等敏感字段一律不接受客户端传值（分析报告 #2）。
 */
public record RegisterRequest(
        @JsonProperty("user_id")
        @NotBlank(message = "账号不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{2,50}$", message = "账号须为 2-50 位字母/数字/下划线")
        String userId,

        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码最长 64 位")
        String password,

        @JsonProperty("user_name")
        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称最长 50 字")
        String userName,

        @JsonProperty("contact_details")
        @Size(max = 255, message = "联系方式最长 255 字")
        String contactDetails,

        @Pattern(regexp = "male|female|unknown", message = "性别取值不合法")
        String sex) {
}
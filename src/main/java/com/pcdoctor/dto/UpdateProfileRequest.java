package com.pcdoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 本人资料更新（契约 §4.2）。白名单 DTO：仅这 4 个字段，#2。
 */
public record UpdateProfileRequest(
        @JsonProperty("user_name")
        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称最长 50 字")
        String userName,

        @Pattern(regexp = "male|female|unknown", message = "性别取值不合法")
        String sex,

        @JsonProperty("contact_details")
        @Size(max = 255, message = "联系方式最长 255 字")
        String contactDetails,

        @JsonProperty("user_description")
        @Size(max = 255, message = "简介最长 255 字")
        String userDescription) {
}
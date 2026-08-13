package com.pcdoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 管理员更新用户（契约 §4.3 PUT /api/admin/users/{id}）。
 * level 仅管理员接口可传（0/1/2），注册/本人更新接口无此字段（#2）。
 */
public record AdminUserUpdateRequest(
        @JsonProperty("user_name")
        @Size(max = 50, message = "昵称最长 50 字")
        String userName,

        @Size(max = 255, message = "联系方式最长 255 字")
        @JsonProperty("contact_details")
        String contactDetails,

        @JsonProperty("user_description")
        @Size(max = 255, message = "简介最长 255 字")
        String userDescription,

        @Min(value = 0, message = "角色取值 0/1/2")
        @Max(value = 2, message = "角色取值 0/1/2")
        Integer level) {
}
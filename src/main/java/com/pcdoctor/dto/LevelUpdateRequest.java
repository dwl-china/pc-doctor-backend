package com.pcdoctor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 改角色专用（契约 §4.3 PUT /api/admin/users/{id}/level）。
 */
public record LevelUpdateRequest(
        @NotNull(message = "角色不能为空")
        @Min(value = 0, message = "角色取值 0/1/2")
        @Max(value = 2, message = "角色取值 0/1/2")
        Integer level) {
}
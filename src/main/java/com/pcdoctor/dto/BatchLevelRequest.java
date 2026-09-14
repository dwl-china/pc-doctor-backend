package com.pcdoctor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量改用户等级（契约 §4.3 POST /api/admin/users/batch/level）。
 * 逐条复用 {@code UserService.adminUpdateLevel}，因此「不能修改自己的角色」等规则一致。
 */
public record BatchLevelRequest(
        @NotEmpty(message = "请选择至少一项")
        @Size(max = 200, message = "单次最多 200 项")
        List<@NotBlank(message = "账号不能为空") String> ids,

        @NotNull(message = "请选择角色")
        @Min(value = 0, message = "角色取值为 0/1/2")
        @Max(value = 2, message = "角色取值为 0/1/2")
        Integer level) {
}

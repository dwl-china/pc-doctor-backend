package com.pcdoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员改预约状态（契约 §4.3 PUT /api/admin/appointments/{id}/status）。
 *
 * <p>独立于 AppointmentUpdateRequest：那个 DTO 是本人/接单电医/管理员三方共用的，
 * 加 status 字段会让普通用户和电医也能改状态（见 AppointmentService.update 的权限判断）。
 */
public record AdminStatusRequest(
        @JsonProperty("status")
        @NotNull(message = "请选择目标状态")
        @Min(value = 0, message = "状态取值为 0/2")
        @Max(value = 2, message = "状态取值为 0/2")
        Integer status,

        /** 目标状态为「已处理」时可选（用于补登记接单电医）；「待处理」忽略 */
        @JsonProperty("doctor_id")
        @Size(max = 50, message = "电医账号过长")
        String doctorId) {
}

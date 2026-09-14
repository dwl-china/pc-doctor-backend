package com.pcdoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量改预约状态（契约 §4.3 POST /api/admin/appointments/batch/status）。
 *
 * <p>{@code status} 必须 {@code @NotNull}：{@code AppointmentService.adminSetStatus} 内部对 status
 * 拆箱比较，传 null 会 NPE → 500（单条路径靠 {@code AdminStatusRequest} 的同名约束兜住，这里照抄）。
 *
 * <p>{@code doctor_id} 作用于整批：{@code status=1} 时不传则逐条沿用各自原有电医，
 * 排队中的单（原本没有电医）会以 2001 记入失败清单。
 */
public record BatchStatusRequest(
        @NotEmpty(message = "请选择至少一项")
        @Size(max = 200, message = "单次最多 200 项")
        List<@NotNull(message = "id 不能为空") Long> ids,

        @NotNull(message = "请选择目标状态")
        @Min(value = 0, message = "状态取值为 0/1/2")
        @Max(value = 2, message = "状态取值为 0/1/2")
        Integer status,

        @JsonProperty("doctor_id")
        @Size(max = 50, message = "电医账号过长")
        String doctorId) {
}

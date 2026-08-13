package com.pcdoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新预约（契约 §4.4 PUT /api/appointments/{id}），不允许改状态/接单字段。
 */
public record AppointmentUpdateRequest(
        @JsonProperty("problem_description")
        @NotBlank(message = "问题描述不能为空")
        @Size(max = 2000, message = "问题描述最长 2000 字")
        String problemDescription,

        @JsonProperty("category_id")
        Long categoryId,

        @JsonProperty("problem_picture")
        @Size(max = 500, message = "图片路径过长")
        String problemPicture,

        @JsonProperty("available_time")
        @Size(max = 255, message = "期望时间过长")
        String availableTime,

        @JsonProperty("appointment_location")
        @Size(max = 255, message = "地点过长")
        String appointmentLocation) {
}
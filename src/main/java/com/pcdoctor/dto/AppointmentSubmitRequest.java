package com.pcdoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交预约（契约 §4.4 POST /api/appointments）。
 */
public record AppointmentSubmitRequest(
        @JsonProperty("problem_description")
        @NotBlank(message = "问题描述不能为空")
        @Size(max = 2000, message = "问题描述最长 2000 字")
        String problemDescription,

        @JsonProperty("category_id")
        @NotNull(message = "请选择问题分类")
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
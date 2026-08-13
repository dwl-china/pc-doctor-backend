package com.pcdoctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 活动新建/更新（契约 §4.7）。
 */
public record ActivityRequest(
        @NotBlank(message = "活动标题不能为空")
        @Size(max = 255, message = "标题过长")
        String title,

        @NotBlank(message = "活动简介不能为空")
        @Size(max = 255, message = "简介过长")
        String summary,

        @NotBlank(message = "详情文件路径不能为空")
        @Size(max = 255, message = "文件路径过长")
        String file,

        @Size(max = 500, message = "封面路径过长")
        String cover) {
}
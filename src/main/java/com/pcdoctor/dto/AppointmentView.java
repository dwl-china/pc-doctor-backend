package com.pcdoctor.dto;

import java.time.LocalDateTime;

/**
 * 预约视图（契约 §4.4 预约对象），联表带出昵称与分类名，不含联系方式等敏感字段。
 */
public record AppointmentView(
        Long id,
        String userId,
        String userName,
        String problemDescription,
        Long categoryId,
        String categoryName,
        String problemPicture,
        String availableTime,
        String appointmentLocation,
        Integer status,
        String doctorId,
        String doctorName,
        LocalDateTime createTime,
        LocalDateTime appointmentTime,
        LocalDateTime doneTime) {

    /** 复制并替换 problemPicture（公开响应脱敏用，走查建议 #6） */
    public AppointmentView withPicture(String picture) {
        return new AppointmentView(id, userId, userName, problemDescription, categoryId, categoryName,
                picture, availableTime, appointmentLocation, status, doctorId, doctorName,
                createTime, appointmentTime, doneTime);
    }
}
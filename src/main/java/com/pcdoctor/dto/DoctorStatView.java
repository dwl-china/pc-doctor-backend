package com.pcdoctor.dto;

import java.time.LocalDateTime;

/**
 * 电医视图（契约 §4.3 GET /api/admin/doctors），含接单统计。
 */
public record DoctorStatView(
        String userId,
        String userName,
        String sex,
        String contactDetails,
        String userDescription,
        String userPicture,
        LocalDateTime createTime,
        Long acceptedCount,
        Long completedCount) {
}
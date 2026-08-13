package com.pcdoctor.dto;

import java.time.LocalDateTime;

/**
 * 留言视图（契约 §4.5 留言对象）。
 */
public record MessageView(
        Long id,
        Long appointmentId,
        String userId,
        String userName,
        String message,
        String picture,
        LocalDateTime time) {
}
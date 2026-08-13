package com.pcdoctor.dto;

import com.pcdoctor.entity.User;

import java.time.LocalDateTime;

/**
 * 对外暴露的用户信息（契约 §4.1 用户对象），绝不含 password_hash 等敏感字段。
 */
public record UserView(
        String userId,
        String userName,
        String sex,
        Integer level,
        String contactDetails,
        String userDescription,
        String userPicture,
        Boolean mustChangePassword,
        LocalDateTime createTime) {

    public static UserView from(User user) {
        return new UserView(
                user.getUserId(),
                user.getUserName(),
                user.getSex(),
                user.getLevel(),
                user.getContactDetails(),
                user.getUserDescription(),
                user.getUserPicture(),
                user.getMustChangePassword() != null && user.getMustChangePassword() == 1,
                user.getCreateTime());
    }
}
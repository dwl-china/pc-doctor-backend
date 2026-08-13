package com.pcdoctor.dto;

/**
 * 登录/注册响应（契约 §4.1）：JWT + 用户信息。
 */
public record AuthResponse(String token, UserView user) {
}
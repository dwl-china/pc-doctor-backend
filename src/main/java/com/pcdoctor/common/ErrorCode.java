package com.pcdoctor.common;

import lombok.Getter;

/**
 * 业务错误码。
 * 分段约定：1xxx 认证/权限；2xxx 通用数据；3xxx 预约；4xxx 文件；5xxx 系统。
 */
@Getter
public enum ErrorCode {

    // 认证 / 权限
    UNAUTHORIZED(1001, "未登录或登录已过期"),
    FORBIDDEN(1002, "没有权限执行此操作"),
    BAD_CREDENTIALS(1003, "账号或密码错误"),
    ACCOUNT_EXISTS(1004, "该账号已被注册"),
    ACCOUNT_NOT_FOUND(1005, "账号不存在"),
    PASSWORD_TOO_WEAK(1006, "密码强度不足"),
    PASSWORD_MUST_CHANGE(1007, "请先修改初始密码"),

    // 通用数据
    INVALID_PARAM(2001, "参数校验失败"),
    NOT_FOUND(2002, "资源不存在"),
    INVALID_STATUS(2003, "当前状态不允许此操作"),

    // 预约
    APPOINTMENT_NOT_FOUND(3001, "预约不存在"),
    APPOINTMENT_OWNERSHIP(3002, "只能操作自己的预约"),

    // 文件
    FILE_TYPE_NOT_ALLOWED(4001, "不支持的文件类型"),
    FILE_TOO_LARGE(4002, "文件过大"),
    FILE_NOT_FOUND(4003, "文件不存在"),

    // 系统
    SYSTEM_ERROR(5001, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

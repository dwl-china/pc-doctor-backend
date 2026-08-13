package com.pcdoctor.service;

import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.exception.BizException;
import com.pcdoctor.dto.AuthResponse;
import com.pcdoctor.dto.LoginRequest;
import com.pcdoctor.dto.RegisterRequest;
import com.pcdoctor.dto.UserView;
import com.pcdoctor.entity.User;
import com.pcdoctor.mapper.UserMapper;
import com.pcdoctor.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：登录/注册/me。密码全部服务端 bcrypt（分析报告 #3）。
 */
@Slf4j
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 登录。账号不存在与密码错误返回同一错误，不提示账号是否存在（#4.6）。
     */
    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectById(request.userId());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("登录失败: userId={}", request.userId());
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        return new AuthResponse(jwtUtil.generate(user.getUserId()), UserView.from(user));
    }

    /**
     * 注册（注册即登录）。level 恒为 0，不接受客户端传值（#2、#4.4）。
     */
    public AuthResponse register(RegisterRequest request) {
        if (userMapper.selectById(request.userId()) != null) {
            throw new BizException(ErrorCode.ACCOUNT_EXISTS);
        }
        User user = new User();
        user.setUserId(request.userId());
        user.setUserName(request.userName());
        user.setSex(request.sex() == null || request.sex().isBlank() ? "unknown" : request.sex());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setLevel(0);
        user.setMustChangePassword(0);
        user.setContactDetails(request.contactDetails() == null ? "" : request.contactDetails());
        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发下同名账号同时通过存在性检查，插入时主键冲突兜底
            throw new BizException(ErrorCode.ACCOUNT_EXISTS);
        }
        log.info("新用户注册: userId={}", user.getUserId());
        return new AuthResponse(jwtUtil.generate(user.getUserId()), UserView.from(user));
    }

    /** 当前登录用户信息 */
    public UserView me(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return UserView.from(user);
    }
}
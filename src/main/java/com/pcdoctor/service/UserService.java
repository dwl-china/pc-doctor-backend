package com.pcdoctor.service;

import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.exception.BizException;
import com.pcdoctor.dto.ChangePasswordRequest;
import com.pcdoctor.entity.User;
import com.pcdoctor.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * 用户服务：改密、管理员重置密码（#4.2、#4.7）。
 */
@Slf4j
@Service
public class UserService {

    /** 随机密码字符集：去掉 0/O、1/l/I 等易混淆字符 */
    private static final char[] PASSWORD_CHARS =
            "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789!@#$%".toCharArray();
    private static final int RANDOM_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** 本人改密：校验旧密码，bcrypt 更新，并解除强制改密标记 */
    public void changePassword(String userId, ChangePasswordRequest request) {
        if (request.newPassword().equals(request.oldPassword())) {
            throw new BizException(ErrorCode.PASSWORD_TOO_WEAK, "新密码不能与旧密码相同");
        }
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "旧密码错误");
        }
        User update = new User();
        update.setUserId(userId);
        update.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        update.setMustChangePassword(0);
        userMapper.updateById(update);
        log.info("用户改密成功: userId={}", userId);
    }

    /**
     * 管理员重置密码：生成随机密码（不再硬编码 123456，#4.7），
     * 用户下次登录强制改密。
     *
     * @return 新密码（仅此一次返回，不落日志）
     */
    public String resetPassword(String userId) {
        requireUser(userId);
        String newPassword = randomPassword();
        User update = new User();
        update.setUserId(userId);
        update.setPasswordHash(passwordEncoder.encode(newPassword));
        update.setMustChangePassword(1);
        userMapper.updateById(update);
        log.info("管理员重置密码: userId={}", userId);
        return newPassword;
    }

    private User requireUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return user;
    }

    private String randomPassword() {
        StringBuilder sb = new StringBuilder(RANDOM_PASSWORD_LENGTH);
        for (int i = 0; i < RANDOM_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS[RANDOM.nextInt(PASSWORD_CHARS.length)]);
        }
        return sb.toString();
    }
}
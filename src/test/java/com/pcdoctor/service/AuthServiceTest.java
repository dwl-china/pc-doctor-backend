package com.pcdoctor.service;

import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.dto.LoginRequest;
import com.pcdoctor.dto.RegisterRequest;
import com.pcdoctor.entity.User;
import com.pcdoctor.mapper.UserMapper;
import com.pcdoctor.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录/注册安全规则（#4.4、#4.6、#6.1）。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserMapper userMapper;
    @Mock
    JwtUtil jwtUtil;

    PasswordEncoder encoder = new BCryptPasswordEncoder();
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userMapper, encoder, jwtUtil);
    }

    @Test
    @DisplayName("账号不存在与密码错误返回同一错误码（不泄露账号是否存在）")
    void loginDoesNotLeakExistence() {
        when(userMapper.selectById("ghost")).thenReturn(null);
        User existing = new User();
        existing.setUserId("u1");
        existing.setUserName("u1");
        existing.setPasswordHash(encoder.encode("right-pass"));
        existing.setLevel(0);
        existing.setMustChangePassword(0);
        when(userMapper.selectById("u1")).thenReturn(existing);

        ErrorCode ghostError = catchError(() -> service.login(new LoginRequest("ghost", "whatever-123")));
        ErrorCode wrongPassError = catchError(() -> service.login(new LoginRequest("u1", "wrong-pass-123")));
        assertThat(ghostError).isEqualTo(wrongPassError).isEqualTo(ErrorCode.BAD_CREDENTIALS);
    }

    @Test
    @DisplayName("注册：level 恒为 0、密码 bcrypt 落库")
    void registerForcesLevelZero() {
        when(userMapper.selectById("newuser")).thenReturn(null);
        when(jwtUtil.generate("newuser")).thenReturn("jwt-token");
        service.register(new RegisterRequest("newuser", "Passw0rd!123", "nick", null, "male"));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User inserted = captor.getValue();
        assertThat(inserted.getLevel()).isZero();
        assertThat(inserted.getMustChangePassword()).isZero();
        assertThat(encoder.matches("Passw0rd!123", inserted.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("重复注册 → ACCOUNT_EXISTS")
    void registerDuplicate() {
        when(userMapper.selectById("dup")).thenReturn(new User());
        assertThatThrownBy(() -> service.register(
                new RegisterRequest("dup", "Passw0rd!123", "nick", null, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_EXISTS));
    }

    @Test
    @DisplayName("注册成功即返回 JWT 与用户信息")
    void registerReturnsAuth() {
        when(userMapper.selectById("ok")).thenReturn(null);
        when(jwtUtil.generate("ok")).thenReturn("jwt-token");
        var response = service.register(new RegisterRequest("ok", "Passw0rd!123", "nick", "qq:1", null));
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().userId()).isEqualTo("ok");
        assertThat(response.user().sex()).isEqualTo("unknown");
    }

    private ErrorCode catchError(Runnable action) {
        try {
            action.run();
            return null;
        } catch (BizException e) {
            return e.getErrorCode();
        }
    }
}
package com.pcdoctor.service;

import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.dto.ChangePasswordRequest;
import com.pcdoctor.entity.User;
import com.pcdoctor.mapper.AppointmentMapper;
import com.pcdoctor.mapper.MessageMapper;
import com.pcdoctor.mapper.ReportMapper;
import com.pcdoctor.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 密码逻辑与管理员操作保护（#4.7、#5.1、#6.1）。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserMapper userMapper;
    @Mock
    AppointmentMapper appointmentMapper;
    @Mock
    MessageMapper messageMapper;
    @Mock
    ReportMapper reportMapper;
    @Mock
    FileService fileService;

    PasswordEncoder encoder = new BCryptPasswordEncoder();
    UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userMapper, appointmentMapper, messageMapper, reportMapper, encoder, fileService);
    }

    private User userWithHash(String hash) {
        User u = new User();
        u.setUserId("u1");
        u.setUserName("u1");
        u.setPasswordHash(hash);
        u.setMustChangePassword(0);
        u.setLevel(0);
        return u;
    }

    @Test
    @DisplayName("改密：旧密码错误 → BAD_CREDENTIALS")
    void changePasswordWrongOld() {
        when(userMapper.selectById("u1")).thenReturn(userWithHash(encoder.encode("correct-old")));
        assertThatThrownBy(() -> service.changePassword("u1",
                new ChangePasswordRequest("wrong-old", "NewPassw0rd!1")))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BAD_CREDENTIALS));
    }

    @Test
    @DisplayName("改密：新旧相同 → PASSWORD_TOO_WEAK（校验先于查库）")
    void changePasswordSameAsOld() {
        assertThatThrownBy(() -> service.changePassword("u1",
                new ChangePasswordRequest("SamePassw0rd!1", "SamePassw0rd!1")))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_TOO_WEAK));
    }

    @Test
    @DisplayName("改密成功：bcrypt 更新并清除强制改密标记")
    void changePasswordOk() {
        when(userMapper.selectById("u1"))
                .thenReturn(userWithHash(encoder.encode("old-pass-123")));
        service.changePassword("u1", new ChangePasswordRequest("old-pass-123", "NewPassw0rd!1"));
        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userMapper).updateById(captor.capture());
        User updated = captor.getValue();
        assertThat(encoder.matches("NewPassw0rd!1", updated.getPasswordHash())).isTrue();
        assertThat(updated.getMustChangePassword()).isZero();
    }

    @Test
    @DisplayName("重置密码：随机 12 位且强制改密=1")
    void resetPasswordRandom() {
        when(userMapper.selectById("u1")).thenReturn(userWithHash("x"));
        String newPassword = service.resetPassword("u1");
        assertThat(newPassword).hasSize(12);
        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getMustChangePassword()).isEqualTo(1);
    }

    @Test
    @DisplayName("管理员不能改自己角色 / 删自己（自我保护先于查库）")
    void adminSelfProtection() {
        assertThatThrownBy(() -> service.adminUpdateLevel("admin", 0, "admin"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS));
        assertThatThrownBy(() -> service.adminDelete("admin", "admin"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS));
    }

    @Test
    @DisplayName("有预约/留言记录的用户禁止删除")
    void adminDeleteWithRecords() {
        when(userMapper.selectById("u1")).thenReturn(userWithHash("x"));
        when(appointmentMapper.selectCount(any())).thenReturn(3L);
        assertThatThrownBy(() -> service.adminDelete("u1", "admin"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS));
    }

    @Test
    @DisplayName("有举报记录的用户禁止删除（回归：漏检 fk_report_reporter 会撞外键 → 500）")
    void adminDeleteWithReports() {
        when(userMapper.selectById("u2")).thenReturn(userWithHash("x"));
        when(appointmentMapper.selectCount(any())).thenReturn(0L);
        when(messageMapper.selectCount(any())).thenReturn(0L);
        when(reportMapper.selectCount(any())).thenReturn(2L);
        assertThatThrownBy(() -> service.adminDelete("u2", "admin"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS));
    }

    @Test
    @DisplayName("三种引用都为空时可正常删除")
    void adminDeleteOk() {
        when(userMapper.selectById("u3")).thenReturn(userWithHash("x"));
        when(appointmentMapper.selectCount(any())).thenReturn(0L);
        when(messageMapper.selectCount(any())).thenReturn(0L);
        when(reportMapper.selectCount(any())).thenReturn(0L);
        service.adminDelete("u3", "admin");
        verify(userMapper).deleteById("u3");
    }

    @Test
    @DisplayName("头像非 jpg/png → FILE_TYPE_NOT_ALLOWED")
    void avatarBadExt() {
        when(userMapper.selectById("u1")).thenReturn(userWithHash("x"));
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("evil.gif");
        assertThatThrownBy(() -> service.updateAvatar("u1", file))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED));
    }
}
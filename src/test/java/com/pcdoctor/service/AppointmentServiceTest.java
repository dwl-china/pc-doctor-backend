package com.pcdoctor.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.dto.AppointmentSubmitRequest;
import com.pcdoctor.dto.AppointmentUpdateRequest;
import com.pcdoctor.entity.Appointment;
import com.pcdoctor.entity.Category;
import com.pcdoctor.entity.User;
import com.pcdoctor.mapper.AppointmentMapper;
import com.pcdoctor.mapper.CategoryMapper;
import com.pcdoctor.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 预约状态机与权限规则（#5.2、#9、#6.1）。
 */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    AppointmentMapper appointmentMapper;
    @Mock
    UserMapper userMapper;
    @Mock
    CategoryMapper categoryMapper;
    @Mock
    FileService fileService;

    AppointmentService service;

    Appointment queued;
    Appointment processing;
    Appointment done;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointmentMapper, userMapper, categoryMapper, fileService);
        // 纯 Mockito 环境无 Spring 上下文，MyBatis-Plus 的 Lambda 包装器依赖 TableInfo 缓存，
        // 需显式预热（否则 CI 上测试类执行顺序不同会抛 "can not find lambda cache"）
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), Appointment.class);
        queued = appointment(1L, "u1", Appointment.STATUS_QUEUED, null);
        processing = appointment(2L, "u1", Appointment.STATUS_PROCESSING, "doc1");
        done = appointment(3L, "u1", Appointment.STATUS_DONE, "doc1");
        lenient().when(userMapper.selectById("u1")).thenReturn(user("u1", "user1"));
        lenient().when(userMapper.selectById("doc1")).thenReturn(user("doc1", "doctor1"));
        Category category = new Category();
        category.setId(1L);
        category.setName("系统问题");
        lenient().when(categoryMapper.selectById(1L)).thenReturn(category);
    }

    @Nested
    @DisplayName("接单状态机")
    class Accept {

        @Test
        @DisplayName("排队单接单成功，写入电医与接单时间")
        void acceptQueued() {
            when(appointmentMapper.update(any(), any())).thenReturn(1);
            when(appointmentMapper.selectById(1L)).thenReturn(processing);
            var view = service.accept(1L, "doc1");
            assertThat(view.status()).isEqualTo(Appointment.STATUS_PROCESSING);
            assertThat(view.doctorId()).isEqualTo("doc1");
        }

        @Test
        @DisplayName("已被接单的单再抢单 → INVALID_STATUS")
        void acceptConflict() {
            when(appointmentMapper.update(any(), any())).thenReturn(0);
            when(appointmentMapper.selectById(1L)).thenReturn(processing);
            assertThatThrownBy(() -> service.accept(1L, "doc2"))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS));
        }

        @Test
        @DisplayName("不存在的预约 → APPOINTMENT_NOT_FOUND")
        void acceptMissing() {
            when(appointmentMapper.update(any(), any())).thenReturn(0);
            when(appointmentMapper.selectById(9L)).thenReturn(null);
            assertThatThrownBy(() -> service.accept(9L, "doc1"))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("完成状态机")
    class Complete {

        @Test
        @DisplayName("接单者本人完成 1→2 成功")
        void completeByDoctor() {
            when(appointmentMapper.update(any(), any())).thenReturn(1);
            when(appointmentMapper.selectById(2L)).thenReturn(done);
            var view = service.complete(2L, "doc1");
            assertThat(view.status()).isEqualTo(Appointment.STATUS_DONE);
        }

        @Test
        @DisplayName("非接单者完成 → APPOINTMENT_OWNERSHIP")
        void completeByOtherDoctor() {
            when(appointmentMapper.update(any(), any())).thenReturn(0);
            when(appointmentMapper.selectById(2L)).thenReturn(processing);
            assertThatThrownBy(() -> service.complete(2L, "doc2"))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_OWNERSHIP));
        }

        @Test
        @DisplayName("已完成单重复完成 → INVALID_STATUS")
        void completeDone() {
            when(appointmentMapper.update(any(), any())).thenReturn(0);
            when(appointmentMapper.selectById(3L)).thenReturn(done);
            assertThatThrownBy(() -> service.complete(3L, "doc1"))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS));
        }
    }

    @Nested
    @DisplayName("更新权限")
    class Update {

        @Test
        @DisplayName("本人改未接单单 → 成功")
        void ownerUpdateQueued() {
            when(appointmentMapper.selectById(1L)).thenReturn(queued);
            service.update(1L, request(), "u1", false);
        }

        @Test
        @DisplayName("他人改单 → APPOINTMENT_OWNERSHIP")
        void otherUserUpdate() {
            when(appointmentMapper.selectById(1L)).thenReturn(queued);
            assertThatThrownBy(() -> service.update(1L, request(), "u2", false))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_OWNERSHIP));
        }

        @Test
        @DisplayName("电医改自己接的单(处理中) → 成功；已完成单 → 拒绝")
        void doctorUpdateOwn() {
            when(appointmentMapper.selectById(2L)).thenReturn(processing);
            when(appointmentMapper.selectById(3L)).thenReturn(done);
            service.update(2L, request(), "doc1", false);
            assertThatThrownBy(() -> service.update(3L, request(), "doc1", false))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_OWNERSHIP));
        }

        @Test
        @DisplayName("管理员可改任意状态")
        void adminUpdateAny() {
            when(appointmentMapper.selectById(3L)).thenReturn(done);
            service.update(3L, request(), "admin", true);
        }
    }

    @Nested
    @DisplayName("删除权限")
    class Delete {

        @Test
        @DisplayName("本人删未接单单 → 成功")
        void ownerDeleteQueued() {
            when(appointmentMapper.selectById(1L)).thenReturn(queued);
            service.delete(1L, "u1", false);
        }

        @Test
        @DisplayName("本人删已接单单 → 拒绝；管理员可删")
        void ownerDeleteProcessing() {
            when(appointmentMapper.selectById(2L)).thenReturn(processing);
            assertThatThrownBy(() -> service.delete(2L, "u1", false))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_OWNERSHIP));
            service.delete(2L, "u1", true);
        }
    }

    @Nested
    @DisplayName("提交")
    class Submit {

        @Test
        @DisplayName("分类不存在 → INVALID_PARAM")
        void submitBadCategory() {
            when(categoryMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> service.submit(
                    new AppointmentSubmitRequest("desc", 99L, null, null, null), "u1"))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
        }

        @Test
        @DisplayName("提交成功状态为排队")
        void submitOk() {
            // insert 后回填自增 id（模拟 MyBatis-Plus 行为），detail 回查能命中
            org.mockito.Mockito.doAnswer(inv -> {
                inv.<Appointment>getArgument(0).setId(100L);
                return 1;
            }).when(appointmentMapper).insert(any(Appointment.class));
            when(appointmentMapper.selectById(100L)).thenReturn(appointment(100L, "u1", Appointment.STATUS_QUEUED, null));
            service.submit(new AppointmentSubmitRequest("desc", 1L, null, null, null), "u1");
            org.mockito.ArgumentCaptor<Appointment> captor =
                    org.mockito.ArgumentCaptor.forClass(Appointment.class);
            org.mockito.Mockito.verify(appointmentMapper).insert(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(Appointment.STATUS_QUEUED);
            assertThat(captor.getValue().getUserId()).isEqualTo("u1");
        }
    }

    private AppointmentUpdateRequest request() {
        return new AppointmentUpdateRequest("new desc", 1L, null, null, null);
    }

    private Appointment appointment(Long id, String owner, int status, String doctorId) {
        Appointment a = new Appointment();
        a.setId(id);
        a.setUserId(owner);
        a.setStatus(status);
        a.setDoctorId(doctorId);
        a.setCategoryId(1L);
        a.setProblemDescription("d");
        return a;
    }

    private User user(String id, String name) {
        User u = new User();
        u.setUserId(id);
        u.setUserName(name);
        return u;
    }
}
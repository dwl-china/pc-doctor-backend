package com.pcdoctor.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.dto.AppointmentSubmitRequest;
import com.pcdoctor.dto.AppointmentUpdateRequest;
import com.pcdoctor.entity.Appointment;
import com.pcdoctor.entity.AppointmentMessage;
import com.pcdoctor.entity.Category;
import com.pcdoctor.entity.User;
import com.pcdoctor.mapper.AppointmentMapper;
import com.pcdoctor.mapper.CategoryMapper;
import com.pcdoctor.mapper.MessageMapper;
import com.pcdoctor.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预约状态机与权限规则（#5.2、#9、#6.1）。
 *
 * <p>2026-11 起只有两态：0 待处理 / 2 已处理，接单即完成。
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
    MessageMapper messageMapper;
    @Mock
    FileService fileService;
    @Mock
    ReportService reportService;

    AppointmentService service;

    Appointment queued;
    Appointment accepted;
    Appointment done;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointmentMapper, userMapper, categoryMapper, messageMapper,
                fileService, reportService);
        // 纯 Mockito 环境无 Spring 上下文，MyBatis-Plus 的 Lambda 包装器依赖 TableInfo 缓存，
        // 需显式预热（否则 CI 上测试类执行顺序不同会抛 "can not find lambda cache"）
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), Appointment.class);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), AppointmentMessage.class);
        queued = appointment(1L, "u1", Appointment.STATUS_QUEUED, null);
        accepted = appointment(2L, "u1", Appointment.STATUS_DONE, "doc1");
        done = appointment(3L, "u1", Appointment.STATUS_DONE, "doc1");
        lenient().when(userMapper.selectById("u1")).thenReturn(user("u1", "user1"));
        // doc1 必须是 level=1（管理员改状态要校验目标账号确实是电医）
        lenient().when(userMapper.selectById("doc1")).thenReturn(doctor("doc1", "doctor1"));
        Category category = new Category();
        category.setId(1L);
        category.setName("系统问题");
        lenient().when(categoryMapper.selectById(1L)).thenReturn(category);
    }

    @Nested
    @DisplayName("接单（0→2，接单即完成）")
    class Accept {

        @Test
        @DisplayName("待处理单接单成功：写入电医、接单时间与完成时间")
        void acceptQueued() {
            when(appointmentMapper.update(any(), any())).thenReturn(1);
            when(appointmentMapper.selectById(1L)).thenReturn(accepted);
            var view = service.accept(1L, "doc1");
            assertThat(view.status()).isEqualTo(Appointment.STATUS_DONE);
            assertThat(view.doctorId()).isEqualTo("doc1");
            assertThat(captureUpdateSet())
                    .contains("status").contains("doctor_id")
                    .contains("appointment_time").contains("done_time");
        }

        @Test
        @DisplayName("已被接单的单再抢单 → INVALID_STATUS")
        void acceptConflict() {
            when(appointmentMapper.update(any(), any())).thenReturn(0);
            when(appointmentMapper.selectById(2L)).thenReturn(accepted);
            assertThatThrownBy(() -> service.accept(2L, "doc2"))
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
    @DisplayName("更新权限")
    class Update {

        @Test
        @DisplayName("本人改待处理且未接单的单 → 成功")
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
        @DisplayName("已接单（已处理）后本人不可改 → INVALID_STATUS；接单电医也不可改")
        void lockedAfterAccepted() {
            when(appointmentMapper.selectById(2L)).thenReturn(accepted);
            assertThatThrownBy(() -> service.update(2L, request(), "u1", false))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS));
            assertThatThrownBy(() -> service.update(2L, request(), "doc1", false))
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
        @DisplayName("本人删待处理且未接单的单 → 成功")
        void ownerDeleteQueued() {
            when(appointmentMapper.selectById(1L)).thenReturn(queued);
            service.delete(1L, "u1", false, false);
        }

        @Test
        @DisplayName("本人删已接单的单 → INVALID_STATUS；管理员可删")
        void ownerDeleteAccepted() {
            when(appointmentMapper.selectById(2L)).thenReturn(accepted);
            assertThatThrownBy(() -> service.delete(2L, "u1", false, false))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS));
            service.delete(2L, "u1", true, false);
        }

        @Test
        @DisplayName("电医删自己接的单 → 成功；电医删他人单 → APPOINTMENT_OWNERSHIP")
        void doctorDeleteOwn() {
            when(appointmentMapper.selectById(2L)).thenReturn(accepted);
            service.delete(2L, "doc1", false, true);
            assertThatThrownBy(() -> service.delete(2L, "doc2", false, true))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_OWNERSHIP));
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
        @DisplayName("提交成功状态为待处理")
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

    @Nested
    @DisplayName("管理员改状态")
    class AdminSetStatus {

        @Test
        @DisplayName("只接受 0/2：传 1 → INVALID_PARAM 且不落库")
        void rejectProcessingStatus() {
            assertThatThrownBy(() -> service.adminSetStatus(1L, 1, "doc1"))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
            verify(appointmentMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("补登记的电医不是电医（普通用户/不存在）→ INVALID_PARAM")
        void toDoneNotADoctor() {
            when(appointmentMapper.selectById(1L)).thenReturn(queued);
            when(userMapper.selectById("nobody")).thenReturn(null);
            assertThatThrownBy(() -> service.adminSetStatus(1L, Appointment.STATUS_DONE, "u1"))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
            assertThatThrownBy(() -> service.adminSetStatus(1L, Appointment.STATUS_DONE, "nobody"))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
            verify(appointmentMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("待处理单标记已处理并补登记电医：写入电医、接单时间与完成时间")
        void toDoneOk() {
            when(appointmentMapper.selectById(1L)).thenReturn(queued);
            when(appointmentMapper.update(any(), any())).thenReturn(1);
            when(appointmentMapper.selectById(1L)).thenReturn(accepted);

            var view = service.adminSetStatus(1L, Appointment.STATUS_DONE, "doc1");

            assertThat(view.status()).isEqualTo(Appointment.STATUS_DONE);
            assertThat(view.doctorId()).isEqualTo("doc1");
            String sqlSet = captureUpdateSet();
            assertThat(sqlSet).contains("doctor_id").contains("appointment_time").contains("done_time");
        }

        @Test
        @DisplayName("同状态重复提交：接单电医未变则不覆盖接单时间")
        void sameStatusKeepsAppointmentTime() {
            accepted.setAppointmentTime(java.time.LocalDateTime.now());   // 已有接单时间，不该被刷新
            accepted.setDoneTime(null);
            when(appointmentMapper.selectById(2L)).thenReturn(accepted);
            when(appointmentMapper.update(any(), any())).thenReturn(1);

            service.adminSetStatus(2L, Appointment.STATUS_DONE, "doc1");

            // 只在"原本没有接单时间"时才写 appointment_time
            assertThat(captureUpdateSet()).doesNotContain("appointment_time");
        }

        @Test
        @DisplayName("退回待处理：清空接单电医与两个时间戳")
        void toQueuedClearsFields() {
            when(appointmentMapper.selectById(3L)).thenReturn(done);
            when(appointmentMapper.update(any(), any())).thenReturn(1);

            service.adminSetStatus(3L, Appointment.STATUS_QUEUED, null);

            // 显式 .set(col, null) 生成的是占位符（不是字面 "null"），
            // 真正的清空效果由 AppointmentFlowIntegrationTest 查 DB 断言
            assertThat(captureUpdateSet())
                    .contains("doctor_id").contains("appointment_time").contains("done_time");
        }

        @Test
        @DisplayName("待处理→已处理：缺完成时间则补")
        void toDoneFillsDoneTime() {
            when(appointmentMapper.selectById(1L)).thenReturn(queued);   // doneTime 为 null
            when(appointmentMapper.update(any(), any())).thenReturn(1);

            service.adminSetStatus(1L, Appointment.STATUS_DONE, null);

            assertThat(captureUpdateSet()).contains("done_time");
        }

        @Test
        @DisplayName("并发冲突（读取后被电医抢先变更）→ INVALID_STATUS")
        void casConflict() {
            when(appointmentMapper.selectById(1L)).thenReturn(queued);
            when(appointmentMapper.update(any(), any())).thenReturn(0);
            assertThatThrownBy(() -> service.adminSetStatus(1L, Appointment.STATUS_DONE, "doc1"))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS));
        }

        @Test
        @DisplayName("预约不存在 → APPOINTMENT_NOT_FOUND")
        void notFound() {
            when(appointmentMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> service.adminSetStatus(99L, Appointment.STATUS_QUEUED, null))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_NOT_FOUND));
        }
    }

    /**
     * 捕获传给 mapper 的 UPDATE SET 子句（列名以字面量出现，便于断言"写没写这一列"）。
     * 同时断言第一个入参是 null —— 传了非 null 实体会把它的非空字段合并进 SET 子句。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String captureUpdateSet() {
        ArgumentCaptor<LambdaUpdateWrapper> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(appointmentMapper, atLeastOnce())
                .update(ArgumentMatchers.<Appointment>isNull(), (Wrapper<Appointment>) captor.capture());
        return captor.getValue().getSqlSet();
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
        u.setLevel(0);
        return u;
    }

    private User doctor(String id, String name) {
        User u = user(id, name);
        u.setLevel(1);
        return u;
    }
}

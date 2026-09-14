package com.pcdoctor.service;

import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 批量操作（契约 §4.3）：逐条独立执行、部分成功 + 失败清单。
 */
@ExtendWith(MockitoExtension.class)
class AdminBatchServiceTest {

    @Mock
    UserService userService;
    @Mock
    AppointmentService appointmentService;
    @Mock
    CategoryService categoryService;
    @Mock
    ReportService reportService;

    AdminBatchService service;

    @BeforeEach
    void setUp() {
        service = new AdminBatchService(userService, appointmentService, categoryService, reportService);
    }

    /**
     * 本类最重要的一条断言：批处理方法不能带 @Transactional。
     *
     * <p>加了的话内层单条方法会加入同一事务，catch 掉异常继续循环后事务已被标记 rollback-only，
     * 外层提交时抛 UnexpectedRollbackException → 5001，"部分成功"直接变成整体失败。
     *
     * <p>集成测试**测不出**这一点：BaseIntegrationTest 类级 @Transactional 会让所有内层调用
     * 加入测试事务，而测试永远 rollback、从不 commit，这个陷阱不会暴露。所以只能靠反射锁住。
     */
    @Test
    @DisplayName("批量服务不得标注 @Transactional（否则 catch 后 rollback-only → 提交抛 5001）")
    void batchServiceMustNotBeTransactional() {
        assertThat(AnnotatedElementUtils.findMergedAnnotation(AdminBatchService.class, Transactional.class))
                .as("AdminBatchService 类上不能有 @Transactional")
                .isNull();
        for (Method m : AdminBatchService.class.getDeclaredMethods()) {
            assertThat(AnnotatedElementUtils.findMergedAnnotation(m, Transactional.class))
                    .as("批量方法 %s 不能有 @Transactional", m.getName())
                    .isNull();
        }
    }

    @Nested
    @DisplayName("部分成功")
    class PartialSuccess {

        @Test
        @DisplayName("中间某条抛业务异常：其余照常执行，失败项带 code 与文案")
        void continuesAfterBizException() {
            // 用 doAnswer 按参数条件抛：若直接桩 adminDelete("b",...) ，严格桩模式下
            // 以其它参数调用同一方法会抛 PotentialStubbingProblem，把测试本身带偏
            doAnswer(inv -> {
                if ("b".equals(inv.getArgument(0))) {
                    throw new BizException(ErrorCode.INVALID_STATUS, "该用户存在预约/留言/举报记录，无法删除");
                }
                return null;
            }).when(userService).adminDelete(anyString(), anyString());

            var result = service.deleteUsers(List.of("a", "b", "c"), "admin");

            verify(userService).adminDelete("a", "admin");
            verify(userService).adminDelete("b", "admin");
            verify(userService).adminDelete("c", "admin");
            assertThat(result.total()).isEqualTo(3);
            assertThat(result.success()).isEqualTo(2);
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).id()).isEqualTo("b");
            assertThat(result.failures().get(0).code()).isEqualTo(2003);
            assertThat(result.failures().get(0).reason()).isEqualTo("该用户存在预约/留言/举报记录，无法删除");
        }

        @Test
        @DisplayName("非业务异常（外键约束）被兜住并记为 5001，不得冲出循环")
        void unexpectedExceptionIsCapturedNotPropagated() {
            doAnswer(inv -> {
                if ("b".equals(inv.getArgument(0))) {
                    throw new DataIntegrityViolationException("fk_report_reporter");
                }
                return null;
            }).when(userService).adminDelete(anyString(), anyString());

            var result = service.deleteUsers(List.of("a", "b", "c"), "admin");

            verify(userService).adminDelete("c", "admin");
            assertThat(result.success()).isEqualTo(2);
            assertThat(result.failures().get(0).code()).isEqualTo(5001);
        }

        @Test
        @DisplayName("全部失败时 success 为 0，不变式仍成立")
        void allFail() {
            doThrow(new BizException(ErrorCode.INVALID_STATUS, "该举报已处理"))
                    .when(reportService).handle(any(), anyString());

            var result = service.handleReports(List.of(1L, 2L), "admin");

            assertThat(result.total()).isEqualTo(2);
            assertThat(result.success()).isZero();
            assertThat(result.success() + result.failures().size()).isEqualTo(result.total());
        }
    }

    @Nested
    @DisplayName("入参处理")
    class Ids {

        @Test
        @DisplayName("id 去重保序：total 按去重后计，重复项不重复执行")
        void dedupesIdsKeepingOrder() {
            var result = service.deleteCategories(List.of(3L, 1L, 3L, 1L, 2L));

            verify(categoryService).delete(3L);
            verify(categoryService).delete(1L);
            verify(categoryService).delete(2L);
            assertThat(result.total()).isEqualTo(3);
            assertThat(result.success()).isEqualTo(3);
        }

        @Test
        @DisplayName("空列表：不执行任何操作，total 为 0")
        void emptyList() {
            var result = service.deleteUsers(List.of(), "admin");

            verify(userService, never()).adminDelete(any(), any());
            assertThat(result.total()).isZero();
            assertThat(result.success()).isZero();
            assertThat(result.failures()).isEmpty();
        }
    }

    @Nested
    @DisplayName("参数透传")
    class Delegation {

        @Test
        @DisplayName("批量删预约走管理员通道（isAdmin=true）")
        void deleteAppointmentsUsesAdminFlag() {
            service.deleteAppointments(List.of(7L), "admin");

            verify(appointmentService).delete(7L, "admin", true, false);
        }

        @Test
        @DisplayName("批量改等级把操作者与等级逐条透传")
        void updateLevelForwardsArgs() {
            service.updateUserLevels(List.of("u1", "u2"), 1, "admin");

            verify(userService).adminUpdateLevel("u1", 1, "admin");
            verify(userService).adminUpdateLevel("u2", 1, "admin");
        }

        @Test
        @DisplayName("批量改状态把同一个电医应用到整批")
        void setStatusForwardsDoctorForWholeBatch() {
            service.updateAppointmentStatus(List.of(1L, 2L), 1, "doc1");

            verify(appointmentService).adminSetStatus(1L, 1, "doc1");
            verify(appointmentService).adminSetStatus(2L, 1, "doc1");
        }
    }
}

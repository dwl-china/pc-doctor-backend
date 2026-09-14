package com.pcdoctor.service;

import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.dto.BatchResult;
import com.pcdoctor.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * 管理端批量操作（契约 §4.3）：逐条复用现有单条 service 方法，部分成功 + 失败清单。
 *
 * <p><b>本类刻意不加 {@code @Transactional}</b>，这不是疏忽，是正确性前提：
 * <ul>
 *   <li>不加 → 每次调用单条方法都经 Spring 代理、各自开一个事务；某条抛异常只回滚那一条，循环继续</li>
 *   <li>加了 → 内层方法加入同一事务，catch 掉异常继续循环后事务已被标记 rollback-only，
 *       外层提交时抛 {@code UnexpectedRollbackException} → 5001，前功尽弃</li>
 * </ul>
 *
 * <p>同理，批量逻辑也不能写进 {@code UserService} 里自调用 {@code this.adminDelete(...)} ——
 * 自调用绕过代理，内层方法**完全没有事务**，「查存在 + 查引用 + 删除」不再原子。
 * 这正是本类独立存在、并通过构造注入持有各 service 的原因。
 *
 * <p>失败清单里的 code 取自 {@link ErrorCode}，reason 用 {@code BizException} 的自定义文案
 * （与单条调用看到的完全一致）。
 */
@Slf4j
@Service
public class AdminBatchService {

    private final UserService userService;
    private final AppointmentService appointmentService;
    private final CategoryService categoryService;
    private final ReportService reportService;

    public AdminBatchService(UserService userService, AppointmentService appointmentService,
                             CategoryService categoryService, ReportService reportService) {
        this.userService = userService;
        this.appointmentService = appointmentService;
        this.categoryService = categoryService;
        this.reportService = reportService;
    }

    public BatchResult deleteUsers(List<String> ids, String operatorId) {
        return run(ids, id -> userService.adminDelete(id, operatorId));
    }

    public BatchResult updateUserLevels(List<String> ids, int level, String operatorId) {
        return run(ids, id -> userService.adminUpdateLevel(id, level, operatorId));
    }

    public BatchResult deleteAppointments(List<Long> ids, String operatorId) {
        // 管理员通道：isAdmin=true 可删任意状态，删除逻辑内含留言级联与图片清理
        return run(ids, id -> appointmentService.delete(id, operatorId, true, false));
    }

    public BatchResult updateAppointmentStatus(List<Long> ids, Integer status, String doctorId) {
        return run(ids, id -> appointmentService.adminSetStatus(id, status, doctorId));
    }

    public BatchResult deleteCategories(List<Long> ids) {
        return run(ids, categoryService::delete);
    }

    public BatchResult handleReports(List<Long> ids, String operatorId) {
        return run(ids, id -> reportService.handle(id, operatorId));
    }

    /**
     * 逐条执行：去重保序 → 一条一个事务 → 收集失败项。
     *
     * <p>兜底的 {@code catch (Exception)} 不是防御式编程的客套：外键等约束异常抛的是
     * {@code DataIntegrityViolationException} 而非 {@code BizException}，不兜住就会冲出循环，
     * 管理员看到「系统繁忙」却发现前面几条其实已经删了。
     */
    private <T> BatchResult run(List<T> ids, Consumer<T> action) {
        List<T> unique = new ArrayList<>(new LinkedHashSet<>(ids));
        List<BatchResult.Failure> failures = new ArrayList<>();
        for (T id : unique) {
            try {
                action.accept(id);
            } catch (BizException e) {
                log.info("批量操作单项失败: id={}, code={}, msg={}", id, e.getErrorCode().getCode(), e.getMessage());
                failures.add(new BatchResult.Failure(String.valueOf(id), e.getErrorCode().getCode(), e.getMessage()));
            } catch (Exception e) {
                log.error("批量操作单项异常: id={}", id, e);
                failures.add(new BatchResult.Failure(String.valueOf(id),
                        ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage()));
            }
        }
        log.info("批量操作完成: 共 {} 项, 成功 {}, 失败 {}", unique.size(), unique.size() - failures.size(), failures.size());
        return BatchResult.of(unique.size(), failures);
    }
}

package com.pcdoctor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.dto.ReportRequest;
import com.pcdoctor.dto.ReportView;
import com.pcdoctor.entity.Report;
import com.pcdoctor.exception.BizException;
import com.pcdoctor.mapper.AppointmentMapper;
import com.pcdoctor.mapper.MessageMapper;
import com.pcdoctor.mapper.ReportMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 内容举报（走查建议 #3）：
 * - 任意登录用户可举报预约/留言；同一人同一目标仅一次（唯一键兜底）
 * - 管理端列表（含目标快照）+ 标记已处理
 */
@Slf4j
@Service
public class ReportService {

    private final ReportMapper reportMapper;
    private final AppointmentMapper appointmentMapper;
    private final MessageMapper messageMapper;

    public ReportService(ReportMapper reportMapper, AppointmentMapper appointmentMapper,
                         MessageMapper messageMapper) {
        this.reportMapper = reportMapper;
        this.appointmentMapper = appointmentMapper;
        this.messageMapper = messageMapper;
    }

    /** 提交举报（登录）：校验目标存在，重复举报提示已受理 */
    @Transactional
    public void submit(ReportRequest request, String reporterId) {
        boolean exists = switch (request.targetType()) {
            case Report.TYPE_APPOINTMENT -> appointmentMapper.selectById(request.targetId()) != null;
            case Report.TYPE_MESSAGE -> messageMapper.selectById(request.targetId()) != null;
            default -> false;
        };
        if (!exists) {
            throw new BizException(ErrorCode.NOT_FOUND, "举报对象不存在");
        }
        Report r = new Report();
        r.setTargetType(request.targetType());
        r.setTargetId(request.targetId());
        r.setReason(request.reason());
        r.setReporterId(reporterId);
        r.setStatus(Report.STATUS_PENDING);
        r.setCreateTime(LocalDateTime.now());
        try {
            reportMapper.insert(r);
        } catch (DuplicateKeyException e) {
            // 同一人同一目标仅一次举报（uk_report_once）
            throw new BizException(ErrorCode.INVALID_PARAM, "你已举报过该内容，请等待处理");
        }
        log.info("新举报: type={}, targetId={}, reporter={}", request.targetType(), request.targetId(), reporterId);
    }

    /** 管理端举报列表（含目标快照），status 筛选 */
    public PageView<ReportView> adminList(int page, int size, Integer status) {
        LambdaQueryWrapper<Report> qw = new LambdaQueryWrapper<Report>()
                .eq(status != null, Report::getStatus, status)
                .orderByAsc(Report::getStatus)
                .orderByDesc(Report::getCreateTime);
        return PageView.from(reportMapper.selectPageWithSnapshot(new Page<>(page, size), qw));
    }

    /**
     * 内容被删除后自动了结相关举报（预约/留言删除路径调用）。
     *
     * <p>只更新「待处理」的，已处理的不覆盖（保留原处理人与处理时间）。
     * 不做这件事的话，管理员删掉违规内容后，那条举报会永远停在待处理，
     * 再点「删除预约/删除留言」只会得到「预约不存在/留言不存在」的死胡同。
     *
     * @param targetIds 被删除的目标 id；预约删除时会连同其下留言的 id 一并传入
     */
    @Transactional
    public void autoHandleByTargets(int targetType, Collection<Long> targetIds, String operatorId) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        Report update = new Report();
        update.setStatus(Report.STATUS_HANDLED);
        update.setHandleTime(LocalDateTime.now());
        update.setHandlerId(operatorId);
        int rows = reportMapper.update(update, new LambdaUpdateWrapper<Report>()
                .eq(Report::getTargetType, targetType)
                .in(Report::getTargetId, targetIds)
                .eq(Report::getStatus, Report.STATUS_PENDING));
        if (rows > 0) {
            log.info("随内容删除自动了结举报: targetType={}, targetIds={}, 条数={}", targetType, targetIds, rows);
        }
    }

    /** 标记已处理（管理员） */
    @Transactional
    public void handle(Long id, String adminId) {
        Report r = reportMapper.selectById(id);
        if (r == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "举报不存在");
        }
        if (r.getStatus() == Report.STATUS_HANDLED) {
            throw new BizException(ErrorCode.INVALID_STATUS, "该举报已处理");
        }
        Report update = new Report();
        update.setId(id);
        update.setStatus(Report.STATUS_HANDLED);
        update.setHandleTime(LocalDateTime.now());
        update.setHandlerId(adminId);
        reportMapper.updateById(update);
        log.info("举报已处理: id={}, handler={}", id, adminId);
    }
}

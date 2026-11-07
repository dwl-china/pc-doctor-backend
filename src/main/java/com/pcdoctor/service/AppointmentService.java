package com.pcdoctor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.common.TransactionAfterCommit;
import com.pcdoctor.dto.AppointmentSubmitRequest;
import com.pcdoctor.dto.AppointmentUpdateRequest;
import com.pcdoctor.dto.AppointmentView;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.entity.Appointment;
import com.pcdoctor.entity.Category;
import com.pcdoctor.entity.Report;
import com.pcdoctor.entity.User;
import com.pcdoctor.mapper.AppointmentMapper;
import com.pcdoctor.mapper.CategoryMapper;
import com.pcdoctor.mapper.MessageMapper;
import com.pcdoctor.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约核心服务（#5.2）：
 * - 状态机 0(待处理) →accept→ 2(已处理)：接单即完成，CAS 条件更新防并发抢单
 * - 权限：本人可改/删待处理且未被接单的预约；电医/管理员可接单；管理员可删
 * - 列表联表脱敏（#7）
 */
@Slf4j
@Service
public class AppointmentService {

    private final AppointmentMapper appointmentMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final MessageMapper messageMapper;
    private final FileService fileService;
    private final ReportService reportService;

    public AppointmentService(AppointmentMapper appointmentMapper, UserMapper userMapper,
                              CategoryMapper categoryMapper, MessageMapper messageMapper,
                              FileService fileService, ReportService reportService) {
        this.appointmentMapper = appointmentMapper;
        this.userMapper = userMapper;
        this.categoryMapper = categoryMapper;
        this.messageMapper = messageMapper;
        this.fileService = fileService;
        this.reportService = reportService;
    }

    /** 分页列表（公开）：status/user_id/doctor_id 筛选，mine=1 需登录；联表出昵称与分类名 */
    public PageView<AppointmentView> list(int page, int size, Integer status, String userId,
                                          String doctorId, boolean mine, String currentUser, boolean isAdmin) {
        if (mine && currentUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        QueryWrapper<Appointment> qw = new QueryWrapper<>();
        if (status != null) {
            qw.eq("status", status);
        }
        if (userId != null && !userId.isBlank()) {
            qw.apply("a.user_id = {0}", userId);
        }
        if (doctorId != null && !doctorId.isBlank()) {
            qw.apply("a.doctor_id = {0}", doctorId);
        }
        if (mine) {
            qw.apply("a.user_id = {0}", currentUser);
        }
        qw.last("ORDER BY a.create_time DESC");
        PageView<AppointmentView> pv = PageView.from(appointmentMapper.selectPageWithDetails(new Page<>(page, size), qw));
        return sanitizePictures(pv, currentUser, isAdmin);
    }

    /** 详情（公开）：预约 + 发单人/电医/分类；图片按可见性脱敏（#6） */
    public AppointmentView detail(Long id, String currentUser, boolean isAdmin) {
        Appointment a = requireAppointment(id);
        User owner = userMapper.selectById(a.getUserId());
        User doctor = a.getDoctorId() == null ? null : userMapper.selectById(a.getDoctorId());
        Category category = a.getCategoryId() == null ? null : categoryMapper.selectById(a.getCategoryId());
        AppointmentView view = toView(a, owner, doctor, category);
        return canSeePicture(view, currentUser, isAdmin) ? view : view.withPicture(null);
    }

    @Transactional
    public AppointmentView submit(AppointmentSubmitRequest request, String userId) {
        if (categoryMapper.selectById(request.categoryId()) == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "分类不存在");
        }
        Appointment a = new Appointment();
        a.setUserId(userId);
        a.setProblemDescription(request.problemDescription());
        a.setCategoryId(request.categoryId());
        a.setProblemPicture(request.problemPicture());
        a.setAvailableTime(request.availableTime());
        a.setAppointmentLocation(request.appointmentLocation());
        a.setStatus(Appointment.STATUS_QUEUED);
        appointmentMapper.insert(a);
        log.info("新预约: id={}, userId={}", a.getId(), userId);
        return detail(a.getId(), userId, false);
    }

    /**
     * 更新预约：本人可改自己待处理且未被接单的预约；管理员可改任意预约。
     *
     * <p>接单即完成，所以一旦被接单（doctorId 非空）预约就不可再编辑。
     */
    @Transactional
    public AppointmentView update(Long id, AppointmentUpdateRequest request, String currentUser, boolean isAdmin) {
        Appointment a = requireAppointment(id);
        boolean owner = a.getUserId().equals(currentUser);
        boolean editableByOwner = owner && a.getStatus() == Appointment.STATUS_QUEUED
                && a.getDoctorId() == null;
        if (!isAdmin && !editableByOwner) {
            if (owner) {
                throw new BizException(ErrorCode.INVALID_STATUS, "当前状态不允许修改");
            }
            throw new BizException(ErrorCode.APPOINTMENT_OWNERSHIP);
        }
        if (request.categoryId() != null && categoryMapper.selectById(request.categoryId()) == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "分类不存在");
        }
        Appointment update = new Appointment();
        update.setId(id);
        update.setProblemDescription(request.problemDescription());
        update.setCategoryId(request.categoryId());
        update.setProblemPicture(request.problemPicture());
        update.setAvailableTime(request.availableTime());
        update.setAppointmentLocation(request.appointmentLocation());
        appointmentMapper.updateById(update);
        return detail(id, currentUser, isAdmin);
    }

    /** 撤销/删除：本人（待处理且未被接单）或管理员；接单者可删自己的单；连带清理问题图片文件（#5.10） */
    @Transactional
    public void delete(Long id, String currentUser, boolean isAdmin, boolean isDoctor) {
        Appointment a = requireAppointment(id);
        boolean owner = a.getUserId().equals(currentUser);
        boolean acceptedDoctor = a.getDoctorId() != null && a.getDoctorId().equals(currentUser);
        if (!isAdmin) {
            if (owner) {
                if (a.getStatus() != Appointment.STATUS_QUEUED || a.getDoctorId() != null) {
                    throw new BizException(ErrorCode.INVALID_STATUS, "仅待处理的预约可撤销");
                }
            } else if (!(isDoctor && acceptedDoctor)) {
                throw new BizException(ErrorCode.APPOINTMENT_OWNERSHIP);
            }
        }
        // 先记下留言 id：预约删掉后这些留言的内容也没了，相关举报要一并了结
        List<Long> messageIds = messageMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.pcdoctor.entity.AppointmentMessage>()
                                .select(com.pcdoctor.entity.AppointmentMessage::getId)
                                .eq(com.pcdoctor.entity.AppointmentMessage::getAppointmentId, id))
                .stream().map(com.pcdoctor.entity.AppointmentMessage::getId).toList();
        // 先删留言再删预约（fk_message_appointment 无级联删除；走查 bug#1：有留言的预约撤销报 500）
        messageMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.pcdoctor.entity.AppointmentMessage>()
                .eq(com.pcdoctor.entity.AppointmentMessage::getAppointmentId, id));
        String picture = a.getProblemPicture();
        appointmentMapper.deleteById(id);
        // 内容已不存在，相关举报一并置为已处理（只动待处理的），避免留下点进去报错的待处理项
        reportService.autoHandleByTargets(Report.TYPE_APPOINTMENT, List.of(id), currentUser);
        reportService.autoHandleByTargets(Report.TYPE_MESSAGE, messageIds, currentUser);
        TransactionAfterCommit.run(() -> fileService.deleteManaged(picture));
        log.info("预约删除: id={}, operator={}", id, currentUser);
    }

    /**
     * 接单（电医/管理员）：0→2，接单即完成。
     *
     * <p>CAS 条件同时要求「未接单」：两个电医并发点接单时只有一个能成功；
     * 接单时间与完成时间在同一条 UPDATE 里写入。
     */
    @Transactional
    public AppointmentView accept(Long id, String doctorId) {
        LocalDateTime now = LocalDateTime.now();
        int rows = appointmentMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Appointment>()
                .eq(Appointment::getId, id)
                .eq(Appointment::getStatus, Appointment.STATUS_QUEUED)
                .isNull(Appointment::getDoctorId)
                .set(Appointment::getStatus, Appointment.STATUS_DONE)
                .set(Appointment::getDoctorId, doctorId)
                .set(Appointment::getAppointmentTime, now)
                .set(Appointment::getDoneTime, now));
        if (rows == 0) {
            Appointment a = appointmentMapper.selectById(id);
            if (a == null) {
                throw new BizException(ErrorCode.APPOINTMENT_NOT_FOUND);
            }
            throw new BizException(ErrorCode.INVALID_STATUS, "该预约已被接单或状态不允许");
        }
        log.info("接单并完成: appointmentId={}, doctorId={}", id, doctorId);
        return detail(id, doctorId, false);
    }

    /**
     * 管理员改状态（契约 §4.3）：电医误操作时的兜底修正入口。
     *
     * <p>只有两个状态：0 待处理 / 2 已处理。语义是「按目标状态反推字段」：
     * <ul>
     *   <li>0 待处理 → 清空接单电医与两个时间戳</li>
     *   <li>2 已处理 → 保留接单信息，缺完成时间则补；可顺带补登记接单电医</li>
     * </ul>
     *
     * <p>两句实现上的注意：MyBatis-Plus 默认不更新 null 字段，清空必须显式
     * {@code .set(col, null)}；且只能 {@code update(null, wrapper)}，传非 null 实体会把它的
     * 非空字段合并进 SET 子句。另外用「比对读到的旧状态」做 CAS，避免管理员读取与写入之间
     * 电医刚接单、接单信息被静默覆盖。
     */
    @Transactional
    public AppointmentView adminSetStatus(Long id, Integer status, String doctorId) {
        if (status == null || (status != Appointment.STATUS_QUEUED && status != Appointment.STATUS_DONE)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "状态只能是待处理或已处理");
        }
        Appointment a = requireAppointment(id);
        int oldStatus = a.getStatus();
        String requested = (doctorId == null || doctorId.isBlank()) ? null : doctorId;

        LambdaUpdateWrapper<Appointment> uw = new LambdaUpdateWrapper<Appointment>()
                .eq(Appointment::getId, id)
                .eq(Appointment::getStatus, oldStatus)          // 并发保护：状态被他人变更则改不动
                .set(Appointment::getStatus, status);

        if (status == Appointment.STATUS_QUEUED) {
            uw.set(Appointment::getDoctorId, null)
                    .set(Appointment::getAppointmentTime, null)
                    .set(Appointment::getDoneTime, null);
        } else {
            if (requested != null) {
                requireDoctor(requested);
                uw.set(Appointment::getDoctorId, requested);
                if (a.getAppointmentTime() == null) {
                    uw.set(Appointment::getAppointmentTime, LocalDateTime.now());
                }
            }
            if (a.getDoneTime() == null) {
                uw.set(Appointment::getDoneTime, LocalDateTime.now());
            }
        }

        int rows = appointmentMapper.update(null, uw);
        if (rows == 0) {
            throw new BizException(ErrorCode.INVALID_STATUS, "预约状态已被他人变更，请刷新后重试");
        }
        log.info("管理员改预约状态: id={}, {}→{}, doctor={}", id, oldStatus, status,
                requested != null ? requested : a.getDoctorId());
        return detail(id, null, true);
    }

    /** 校验目标账号确实是电医（level 严格 == 1）：不校验会撞 fk_appointment_doctor → 5001；
     *  放宽成 &gt;=1 则会把管理员/普通用户派成接单人，造出电医工作台查不到的幽灵单。
     *  （管理员本人接单走 accept，不经过这里。） */
    private void requireDoctor(String userId) {
        User u = userMapper.selectById(userId);
        if (u == null || !Integer.valueOf(1).equals(u.getLevel())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "接单电医不存在或该账号不是电医");
        }
    }

    private Appointment requireAppointment(Long id) {
        Appointment a = appointmentMapper.selectById(id);
        if (a == null) {
            throw new BizException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        return a;
    }

    /** 图片可见性：仅管理员、发单本人、该单电医可见（走查建议 #6：预约图片不公开） */
    private boolean canSeePicture(AppointmentView v, String currentUser, boolean isAdmin) {
        if (isAdmin || v.problemPicture() == null) {
            return true;
        }
        if (currentUser == null) {
            return false;
        }
        return currentUser.equals(v.userId()) || currentUser.equals(v.doctorId());
    }

    /** 列表按可见性脱敏：公开访问者拿不到图片 URL（UUID 文件名不可猜测，#6） */
    private PageView<AppointmentView> sanitizePictures(PageView<AppointmentView> pv, String currentUser, boolean isAdmin) {
        if (isAdmin) {
            return pv;
        }
        return new PageView<>(pv.total(), pv.records().stream()
                .map(v -> canSeePicture(v, currentUser, isAdmin) ? v : v.withPicture(null))
                .toList());
    }

    private AppointmentView toView(Appointment a, User owner, User doctor, Category category) {
        return new AppointmentView(
                a.getId(), a.getUserId(), owner == null ? null : owner.getUserName(),
                a.getProblemDescription(), a.getCategoryId(), category == null ? null : category.getName(),
                a.getProblemPicture(), a.getAvailableTime(), a.getAppointmentLocation(),
                a.getStatus(), a.getDoctorId(), doctor == null ? null : doctor.getUserName(),
                a.getCreateTime(), a.getAppointmentTime(), a.getDoneTime());
    }
}
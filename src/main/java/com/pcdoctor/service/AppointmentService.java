package com.pcdoctor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.pcdoctor.entity.User;
import com.pcdoctor.mapper.AppointmentMapper;
import com.pcdoctor.mapper.CategoryMapper;
import com.pcdoctor.mapper.MessageMapper;
import com.pcdoctor.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 预约核心服务（#5.2）：
 * - 状态机 0(排队) →accept→ 1(处理中) →complete→ 2(已完成)，CAS 条件更新防并发抢单
 * - 权限：本人可改/删未接单预约；电医仅能改/完成自己接的单（#9）；管理员可删
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

    public AppointmentService(AppointmentMapper appointmentMapper, UserMapper userMapper,
                              CategoryMapper categoryMapper, MessageMapper messageMapper,
                              FileService fileService) {
        this.appointmentMapper = appointmentMapper;
        this.userMapper = userMapper;
        this.categoryMapper = categoryMapper;
        this.messageMapper = messageMapper;
        this.fileService = fileService;
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
     * 更新预约：本人可改自己未接单的预约；电医仅可改自己接的单（#9）。
     */
    @Transactional
    public AppointmentView update(Long id, AppointmentUpdateRequest request, String currentUser, boolean isAdmin) {
        Appointment a = requireAppointment(id);
        boolean owner = a.getUserId().equals(currentUser);
        boolean acceptedDoctor = a.getDoctorId() != null && a.getDoctorId().equals(currentUser);
        if (!isAdmin && !(owner && a.getStatus() == Appointment.STATUS_QUEUED)
                && !(acceptedDoctor && a.getStatus() == Appointment.STATUS_PROCESSING)) {
            if (owner || acceptedDoctor) {
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

    /** 撤销/删除：本人（未接单）或管理员；电医可删自己接的单（处置违法内容，走查建议 #3）；连带清理问题图片文件（#5.10） */
    @Transactional
    public void delete(Long id, String currentUser, boolean isAdmin, boolean isDoctor) {
        Appointment a = requireAppointment(id);
        boolean owner = a.getUserId().equals(currentUser);
        boolean acceptedDoctor = a.getDoctorId() != null && a.getDoctorId().equals(currentUser);
        if (!isAdmin) {
            if (owner) {
                if (a.getStatus() != Appointment.STATUS_QUEUED) {
                    throw new BizException(ErrorCode.INVALID_STATUS, "仅排队中的预约可撤销");
                }
            } else if (!(isDoctor && acceptedDoctor)) {
                throw new BizException(ErrorCode.APPOINTMENT_OWNERSHIP);
            }
        }
        // 先删留言再删预约（fk_message_appointment 无级联删除；走查 bug#1：有留言的预约撤销报 500）
        messageMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.pcdoctor.entity.AppointmentMessage>()
                .eq(com.pcdoctor.entity.AppointmentMessage::getAppointmentId, id));
        String picture = a.getProblemPicture();
        appointmentMapper.deleteById(id);
        TransactionAfterCommit.run(() -> fileService.deleteManaged(picture));
        log.info("预约删除: id={}, operator={}", id, currentUser);
    }

    /** 接单（电医）：0→1 写 doctor_id + appointment_time；CAS 防并发抢单 */
    @Transactional
    public AppointmentView accept(Long id, String doctorId) {
        int rows = appointmentMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Appointment>()
                .eq(Appointment::getId, id)
                .eq(Appointment::getStatus, Appointment.STATUS_QUEUED)
                .set(Appointment::getStatus, Appointment.STATUS_PROCESSING)
                .set(Appointment::getDoctorId, doctorId)
                .set(Appointment::getAppointmentTime, LocalDateTime.now()));
        if (rows == 0) {
            Appointment a = appointmentMapper.selectById(id);
            if (a == null) {
                throw new BizException(ErrorCode.APPOINTMENT_NOT_FOUND);
            }
            throw new BizException(ErrorCode.INVALID_STATUS, "该预约已被接单或状态不允许");
        }
        log.info("接单: appointmentId={}, doctorId={}", id, doctorId);
        return detail(id, doctorId, false);
    }

    /** 完成（电医）：1→2 写 done_time；仅接单者本人（#9），CAS 防重复完成 */
    @Transactional
    public AppointmentView complete(Long id, String doctorId) {
        int rows = appointmentMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Appointment>()
                .eq(Appointment::getId, id)
                .eq(Appointment::getStatus, Appointment.STATUS_PROCESSING)
                .eq(Appointment::getDoctorId, doctorId)
                .set(Appointment::getStatus, Appointment.STATUS_DONE)
                .set(Appointment::getDoneTime, LocalDateTime.now()));
        if (rows == 0) {
            Appointment a = appointmentMapper.selectById(id);
            if (a == null) {
                throw new BizException(ErrorCode.APPOINTMENT_NOT_FOUND);
            }
            if (!doctorId.equals(a.getDoctorId())) {
                throw new BizException(ErrorCode.APPOINTMENT_OWNERSHIP, "只能操作自己接的单");
            }
            throw new BizException(ErrorCode.INVALID_STATUS, "当前状态不允许此操作");
        }
        log.info("完成: appointmentId={}, doctorId={}", id, doctorId);
        return detail(id, doctorId, false);
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
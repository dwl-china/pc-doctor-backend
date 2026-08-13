package com.pcdoctor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.dto.AdminUserUpdateRequest;
import com.pcdoctor.dto.ChangePasswordRequest;
import com.pcdoctor.dto.DoctorStatView;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.dto.UpdateProfileRequest;
import com.pcdoctor.dto.UserView;
import com.pcdoctor.entity.Appointment;
import com.pcdoctor.entity.AppointmentMessage;
import com.pcdoctor.entity.User;
import com.pcdoctor.mapper.AppointmentMapper;
import com.pcdoctor.mapper.MessageMapper;
import com.pcdoctor.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * 用户服务：本人资料/头像/改密（#5.1）、管理员用户管理与重置密码（#4.7）。
 */
@Slf4j
@Service
public class UserService {

    /** 随机密码字符集：去掉 0/O、1/l/I 等易混淆字符 */
    private static final char[] PASSWORD_CHARS =
            "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789!@#$%".toCharArray();
    private static final int RANDOM_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 头像最大边长（服务端裁剪压缩，对齐老前端 300px 约定，#33） */
    private static final int AVATAR_MAX_SIZE = 300;

    private final UserMapper userMapper;
    private final AppointmentMapper appointmentMapper;
    private final MessageMapper messageMapper;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;

    public UserService(UserMapper userMapper, AppointmentMapper appointmentMapper,
                       MessageMapper messageMapper, PasswordEncoder passwordEncoder,
                       FileService fileService) {
        this.userMapper = userMapper;
        this.appointmentMapper = appointmentMapper;
        this.messageMapper = messageMapper;
        this.passwordEncoder = passwordEncoder;
        this.fileService = fileService;
    }

    // ---------- 本人操作 ----------

    public UserView profile(String userId) {
        return UserView.from(requireUser(userId));
    }

    /** 更新资料：DTO 白名单，level/密码等一律不可经此修改（#2） */
    @Transactional
    public UserView updateProfile(String userId, UpdateProfileRequest request) {
        User update = new User();
        update.setUserId(userId);
        update.setUserName(request.userName());
        update.setSex(request.sex() == null || request.sex().isBlank() ? "unknown" : request.sex());
        update.setContactDetails(request.contactDetails() == null ? "" : request.contactDetails());
        update.setUserDescription(request.userDescription());
        userMapper.updateById(update);
        return profile(userId);
    }

    /** 头像上传：仅 jpg/png，服务端裁剪压缩至 300px 内后落盘 */
    @Transactional
    public String updateAvatar(String userId, MultipartFile file) {
        requireUser(userId);
        String ext = extOf(file.getOriginalFilename());
        if (!ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("png")) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "头像仅支持 jpg/png");
        }
        byte[] resized;
        try (InputStream in = file.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "无法识别的图片");
            }
            resized = resize(image, ext.equals("png") ? "png" : "jpg");
        } catch (IOException e) {
            log.error("头像处理失败: userId={}", userId, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        String old = requireUser(userId).getUserPicture();
        String url = fileService.saveBytes("avatar", ext.equals("png") ? "png" : "jpg", resized);
        User update = new User();
        update.setUserId(userId);
        update.setUserPicture(url);
        userMapper.updateById(update);
        // 提交后清理旧头像
        com.pcdoctor.common.TransactionAfterCommit.run(() -> fileService.deleteManaged(old));
        return url;
    }

    /** 本人改密：校验旧密码，bcrypt 更新，并解除强制改密标记 */
    @Transactional
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

    // ---------- 公开查询 ----------

    /** 电医列表（level=1），公开 */
    public PageView<UserView> doctors(int page, int size) {
        Page<User> result = userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<User>()
                        .eq(User::getLevel, 1)
                        .orderByAsc(User::getUserId));
        return new PageView<>(result.getTotal(), result.getRecords().stream().map(UserView::from).toList());
    }

    // ---------- 管理员操作 ----------

    /** 用户列表：按账号/昵称模糊搜索 + 角色筛选 */
    public PageView<UserView> adminList(int page, int size, String userId, String userName, Integer level) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if (userId != null && !userId.isBlank()) {
            qw.like(User::getUserId, userId);
        }
        if (userName != null && !userName.isBlank()) {
            qw.like(User::getUserName, userName);
        }
        if (level != null) {
            qw.eq(User::getLevel, level);
        }
        qw.orderByDesc(User::getCreateTime);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), qw);
        return new PageView<>(result.getTotal(), result.getRecords().stream().map(UserView::from).toList());
    }

    /** 管理员更新用户（可改角色）；禁止修改自己的角色，避免误降权锁死 */
    @Transactional
    public UserView adminUpdate(String id, AdminUserUpdateRequest request, String operatorId) {
        User target = requireUser(id);
        if (request.level() != null && id.equals(operatorId) && !request.level().equals(target.getLevel())) {
            throw new BizException(ErrorCode.INVALID_STATUS, "不能修改自己的角色");
        }
        User update = new User();
        update.setUserId(id);
        if (request.userName() != null) {
            update.setUserName(request.userName());
        }
        if (request.contactDetails() != null) {
            update.setContactDetails(request.contactDetails());
        }
        if (request.userDescription() != null) {
            update.setUserDescription(request.userDescription());
        }
        if (request.level() != null) {
            update.setLevel(request.level());
        }
        userMapper.updateById(update);
        log.info("管理员更新用户: id={}, operator={}", id, operatorId);
        return profile(id);
    }

    @Transactional
    public UserView adminUpdateLevel(String id, int level, String operatorId) {
        if (id.equals(operatorId)) {
            throw new BizException(ErrorCode.INVALID_STATUS, "不能修改自己的角色");
        }
        requireUser(id);
        User update = new User();
        update.setUserId(id);
        update.setLevel(level);
        userMapper.updateById(update);
        log.info("管理员改角色: id={}, level={}, operator={}", id, level, operatorId);
        return profile(id);
    }

    /** 删除用户：禁止删自己；有预约/留言记录的禁止删除（外键引用） */
    @Transactional
    public void adminDelete(String id, String operatorId) {
        if (id.equals(operatorId)) {
            throw new BizException(ErrorCode.INVALID_STATUS, "不能删除自己");
        }
        requireUser(id);
        Long appointments = appointmentMapper.selectCount(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getUserId, id)
                .or()
                .eq(Appointment::getDoctorId, id));
        Long messages = messageMapper.selectCount(new LambdaQueryWrapper<AppointmentMessage>()
                .eq(AppointmentMessage::getUserId, id));
        if (appointments > 0 || messages > 0) {
            throw new BizException(ErrorCode.INVALID_STATUS, "该用户存在预约/留言记录，无法删除");
        }
        userMapper.deleteById(id);
        log.info("管理员删除用户: id={}, operator={}", id, operatorId);
    }

    /** 电医列表 + 接单统计 */
    public PageView<DoctorStatView> adminDoctorStats(int page, int size) {
        return PageView.from(userMapper.selectDoctorStats(new Page<>(page, size)));
    }

    /**
     * 管理员重置密码：生成随机密码（不再硬编码 123456，#4.7），
     * 用户下次登录强制改密。
     *
     * @return 新密码（仅此一次返回，不落日志）
     */
    @Transactional
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

    // ---------- 工具 ----------

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

    private String extOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 等比缩放到 max 边长以内 */
    private byte[] resize(BufferedImage image, String format) throws IOException {
        int w = image.getWidth();
        int h = image.getHeight();
        if (Math.max(w, h) > AVATAR_MAX_SIZE) {
            double ratio = (double) AVATAR_MAX_SIZE / Math.max(w, h);
            int nw = Math.max(1, (int) (w * ratio));
            int nh = Math.max(1, (int) (h * ratio));
            BufferedImage scaled = new BufferedImage(nw, nh,
                    format.equals("png") && image.getColorModel().hasAlpha()
                            ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = scaled.createGraphics();
            g.drawImage(image, 0, 0, nw, nh, null);
            g.dispose();
            image = scaled;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "图片处理失败");
        }
        return out.toByteArray();
    }
}
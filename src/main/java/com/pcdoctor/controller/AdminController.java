package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.AdminStatusRequest;
import com.pcdoctor.dto.AdminUserUpdateRequest;
import com.pcdoctor.dto.AppointmentView;
import com.pcdoctor.dto.BatchIdsRequest;
import com.pcdoctor.dto.BatchLevelRequest;
import com.pcdoctor.dto.BatchResult;
import com.pcdoctor.dto.BatchStatusRequest;
import com.pcdoctor.dto.BatchUserIdsRequest;
import com.pcdoctor.dto.DoctorStatView;
import com.pcdoctor.dto.LevelUpdateRequest;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.dto.UserView;
import com.pcdoctor.service.AdminBatchService;
import com.pcdoctor.service.AppointmentService;
import com.pcdoctor.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * 管理端接口（契约 §4.3）。权限 @PreAuthorize 按接口声明（#4.5、#7）。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminController {

    private final UserService userService;
    private final AppointmentService appointmentService;
    private final AdminBatchService batchService;

    public AdminController(UserService userService, AppointmentService appointmentService,
                           AdminBatchService batchService) {
        this.userService = userService;
        this.appointmentService = appointmentService;
        this.batchService = batchService;
    }

    /** 用户列表：搜索/分页。keyword = 账号或昵称模糊匹配（管理端搜索框用这个） */
    @GetMapping("/users")
    public Result<PageView<UserView>> users(@RequestParam(defaultValue = "1") @Min(value = 1, message = "page 最小为 1") int page,
                                            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 最小为 1") @Max(value = 100, message = "size 最大为 100") int size,
                                            @RequestParam(name = "user_id", required = false) String userId,
                                            @RequestParam(name = "user_name", required = false) String userName,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer level) {
        return Result.ok(userService.adminList(page, size, userId, userName, keyword, level));
    }

    /** 更新用户（可改角色；禁改自己角色） */
    @PutMapping("/users/{id}")
    public Result<UserView> updateUser(@PathVariable String id,
                                       @Valid @RequestBody AdminUserUpdateRequest request,
                                       Authentication authentication) {
        return Result.ok(userService.adminUpdate(id, request, authentication.getName()));
    }

    /** 改角色专用 */
    @PutMapping("/users/{id}/level")
    public Result<UserView> updateLevel(@PathVariable String id,
                                        @Valid @RequestBody LevelUpdateRequest request,
                                        Authentication authentication) {
        return Result.ok(userService.adminUpdateLevel(id, request.level(), authentication.getName()));
    }

    /** 重置密码：随机密码仅此一次返回，用户下次登录强制改密（#4.7） */
    @PostMapping("/users/{id}/reset-password")
    public Result<Map<String, String>> resetPassword(@PathVariable String id) {
        return Result.ok(Map.of("new_password", userService.resetPassword(id)));
    }

    /** 删除用户（禁删自己/有业务记录者） */
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable String id, Authentication authentication) {
        userService.adminDelete(id, authentication.getName());
        return Result.ok();
    }

    /**
     * 改预约状态（契约 §4.3）：电医误操作时的兜底修正入口，按目标状态自动维护
     * 接单电医与时间戳；并发冲突返回 2003（状态已被他人变更）。
     */
    @PutMapping("/appointments/{id}/status")
    public Result<AppointmentView> updateAppointmentStatus(@PathVariable Long id,
                                                           @Valid @RequestBody AdminStatusRequest request) {
        return Result.ok(appointmentService.adminSetStatus(id, request.status(), request.doctorId()));
    }

    // ------------------------------------------------------------------
    // 批量操作（契约 §4.3）：部分成功 + 失败清单，全部走 /api/admin/** 复用类级权限
    // ------------------------------------------------------------------

    @PostMapping("/users/batch/delete")
    public Result<BatchResult> batchDeleteUsers(@Valid @RequestBody BatchUserIdsRequest request,
                                                Authentication authentication) {
        return Result.ok(batchService.deleteUsers(request.ids(), authentication.getName()));
    }

    @PostMapping("/users/batch/level")
    public Result<BatchResult> batchUpdateUserLevel(@Valid @RequestBody BatchLevelRequest request,
                                                    Authentication authentication) {
        return Result.ok(batchService.updateUserLevels(request.ids(), request.level(), authentication.getName()));
    }

    @PostMapping("/appointments/batch/delete")
    public Result<BatchResult> batchDeleteAppointments(@Valid @RequestBody BatchIdsRequest request,
                                                       Authentication authentication) {
        return Result.ok(batchService.deleteAppointments(request.ids(), authentication.getName()));
    }

    @PostMapping("/appointments/batch/status")
    public Result<BatchResult> batchUpdateAppointmentStatus(@Valid @RequestBody BatchStatusRequest request) {
        return Result.ok(batchService.updateAppointmentStatus(request.ids(), request.status(), request.doctorId()));
    }

    @PostMapping("/categories/batch/delete")
    public Result<BatchResult> batchDeleteCategories(@Valid @RequestBody BatchIdsRequest request) {
        return Result.ok(batchService.deleteCategories(request.ids()));
    }

    @PostMapping("/reports/batch/handle")
    public Result<BatchResult> batchHandleReports(@Valid @RequestBody BatchIdsRequest request,
                                                  Authentication authentication) {
        return Result.ok(batchService.handleReports(request.ids(), authentication.getName()));
    }

    /** 电医列表 + 接单统计 */
    @GetMapping("/doctors")
    public Result<PageView<DoctorStatView>> doctors(@RequestParam(defaultValue = "1") @Min(value = 1, message = "page 最小为 1") int page,
                                                    @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 最小为 1") @Max(value = 100, message = "size 最大为 100") int size) {
        return Result.ok(userService.adminDoctorStats(page, size));
    }
}
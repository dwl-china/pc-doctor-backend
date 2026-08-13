package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.AppointmentSubmitRequest;
import com.pcdoctor.dto.AppointmentUpdateRequest;
import com.pcdoctor.dto.AppointmentView;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.service.AppointmentService;
import jakarta.validation.Valid;
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

/**
 * 预约接口（契约 §4.4）。
 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /** 列表（公开；mine=1 需登录） */
    @GetMapping
    public Result<PageView<AppointmentView>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(name = "user_id", required = false) String userId,
                                                  @RequestParam(defaultValue = "0") int mine,
                                                  Authentication authentication) {
        String currentUser = authentication == null ? null : authentication.getName();
        return Result.ok(appointmentService.list(page, size, status, userId, mine == 1, currentUser));
    }

    /** 详情（公开） */
    @GetMapping("/{id}")
    public Result<AppointmentView> detail(@PathVariable Long id) {
        return Result.ok(appointmentService.detail(id));
    }

    /** 提交预约（登录） */
    @PostMapping
    public Result<AppointmentView> submit(@Valid @RequestBody AppointmentSubmitRequest request,
                                          Authentication authentication) {
        return Result.ok(appointmentService.submit(request, authentication.getName()));
    }

    /** 更新预约（登录；本人未接单单 / 电医自己接的单 / 管理员） */
    @PutMapping("/{id}")
    public Result<AppointmentView> update(@PathVariable Long id,
                                          @Valid @RequestBody AppointmentUpdateRequest request,
                                          Authentication authentication) {
        return Result.ok(appointmentService.update(id, request, authentication.getName(),
                isAdmin(authentication)));
    }

    /** 撤销/删除（登录；本人未接单单 / 管理员） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        appointmentService.delete(id, authentication.getName(), isAdmin(authentication));
        return Result.ok();
    }

    /** 接单（电医）：0→1 */
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('DOCTOR')")
    public Result<AppointmentView> accept(@PathVariable Long id, Authentication authentication) {
        return Result.ok(appointmentService.accept(id, authentication.getName()));
    }

    /** 完成（电医，仅接单者本人）：1→2 */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public Result<AppointmentView> complete(@PathVariable Long id, Authentication authentication) {
        return Result.ok(appointmentService.complete(id, authentication.getName()));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
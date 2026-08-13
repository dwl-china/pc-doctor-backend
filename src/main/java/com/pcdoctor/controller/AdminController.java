package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.AdminUserUpdateRequest;
import com.pcdoctor.dto.DoctorStatView;
import com.pcdoctor.dto.LevelUpdateRequest;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.dto.UserView;
import com.pcdoctor.service.UserService;
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

import java.util.Map;

/**
 * 管理端接口（契约 §4.3）。权限 @PreAuthorize 按接口声明（#4.5、#7）。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    /** 用户列表：搜索/分页 */
    @GetMapping("/users")
    public Result<PageView<UserView>> users(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(name = "user_id", required = false) String userId,
                                            @RequestParam(name = "user_name", required = false) String userName,
                                            @RequestParam(required = false) Integer level) {
        return Result.ok(userService.adminList(page, size, userId, userName, level));
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

    /** 电医列表 + 接单统计 */
    @GetMapping("/doctors")
    public Result<PageView<DoctorStatView>> doctors(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return Result.ok(userService.adminDoctorStats(page, size));
    }
}
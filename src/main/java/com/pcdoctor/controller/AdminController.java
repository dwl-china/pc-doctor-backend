package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端接口（契约 §4.3）。权限用 @PreAuthorize 按接口声明（#4.5、#7），
 * 阶段 4 将补用户列表/改等级/删除等。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    /** 重置密码：随机密码仅此一次返回给管理员，用户下次登录强制改密（#4.7） */
    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, String>> resetPassword(@PathVariable String id) {
        return Result.ok(Map.of("new_password", userService.resetPassword(id)));
    }
}
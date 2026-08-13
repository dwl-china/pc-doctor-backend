package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.ChangePasswordRequest;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.dto.UpdateProfileRequest;
import com.pcdoctor.dto.UserView;
import com.pcdoctor.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 用户自操作与公开用户接口（契约 §4.2）。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public Result<UserView> me(Authentication authentication) {
        return Result.ok(userService.profile(authentication.getName()));
    }

    /** 更新资料：DTO 白名单（#2） */
    @PutMapping("/me")
    public Result<UserView> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                          Authentication authentication) {
        return Result.ok(userService.updateProfile(authentication.getName(), request));
    }

    /** 头像上传：multipart file，jpg/png，服务端裁剪压缩至 300px */
    @PostMapping("/me/avatar")
    public Result<Map<String, String>> updateAvatar(@RequestParam("file") MultipartFile file,
                                                    Authentication authentication) {
        return Result.ok(Map.of("url", userService.updateAvatar(authentication.getName(), file)));
    }

    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                       Authentication authentication) {
        userService.changePassword(authentication.getName(), request);
        return Result.ok();
    }
}
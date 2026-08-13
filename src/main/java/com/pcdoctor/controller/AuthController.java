package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.AuthResponse;
import com.pcdoctor.dto.LoginRequest;
import com.pcdoctor.dto.RegisterRequest;
import com.pcdoctor.dto.UserView;
import com.pcdoctor.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（契约 §4.1）：登录/注册公开，me 需登录。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @GetMapping("/me")
    public Result<UserView> me(Authentication authentication) {
        return Result.ok(authService.me(authentication.getName()));
    }
}
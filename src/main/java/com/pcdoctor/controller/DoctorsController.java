package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.dto.UserView;
import com.pcdoctor.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电医列表（契约 §4.2：GET /api/doctors，公开）。
 */
@RestController
@RequestMapping("/api/doctors")
public class DoctorsController {

    private final UserService userService;

    public DoctorsController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<PageView<UserView>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return Result.ok(userService.doctors(page, size));
    }
}
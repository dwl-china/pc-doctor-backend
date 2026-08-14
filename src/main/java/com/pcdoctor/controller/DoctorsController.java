package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.dto.UserView;
import com.pcdoctor.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * 电医列表（契约 §4.2：GET /api/doctors，公开）。
 */
@RestController
@RequestMapping("/api/doctors")
@Validated
public class DoctorsController {

    private final UserService userService;

    public DoctorsController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<PageView<UserView>> list(@RequestParam(defaultValue = "1") @Min(value = 1, message = "page 最小为 1") int page,
                                           @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 最小为 1") @Max(value = 100, message = "size 最大为 100") int size) {
        return Result.ok(userService.doctors(page, size));
    }
}
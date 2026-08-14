package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.ActivityRequest;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.entity.Activity;
import com.pcdoctor.service.ActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * 活动接口（契约 §4.7）：读公开，写管理员。
 */
@RestController
@RequestMapping("/api/activities")
@Validated
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public Result<PageView<Activity>> list(@RequestParam(defaultValue = "1") @Min(value = 1, message = "page 最小为 1") int page,
                                           @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 最小为 1") @Max(value = 100, message = "size 最大为 100") int size) {
        return Result.ok(activityService.list(page, size));
    }

    @GetMapping("/{id}")
    public Result<Activity> detail(@PathVariable Long id) {
        return Result.ok(activityService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Activity> create(@Valid @RequestBody ActivityRequest request) {
        return Result.ok(activityService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Activity> update(@PathVariable Long id, @Valid @RequestBody ActivityRequest request) {
        return Result.ok(activityService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        activityService.delete(id);
        return Result.ok();
    }
}
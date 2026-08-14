package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.dto.ReportRequest;
import com.pcdoctor.dto.ReportView;
import com.pcdoctor.service.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容举报（走查建议 #3，契约 §4.11）：提交需登录；管理端列表/处理仅管理员。
 */
@RestController
@RequestMapping("/api")
@Validated
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 提交举报（登录） */
    @PostMapping("/reports")
    public Result<Void> submit(@Valid @RequestBody ReportRequest request, Authentication authentication) {
        reportService.submit(request, authentication.getName());
        return Result.ok();
    }

    /** 管理端举报列表（管理员） */
    @GetMapping("/admin/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageView<ReportView>> list(@RequestParam(defaultValue = "1") @Min(value = 1, message = "page 最小为 1") int page,
                                             @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 最小为 1") @Max(value = 100, message = "size 最大为 100") int size,
                                             @RequestParam(required = false) Integer status) {
        return Result.ok(reportService.adminList(page, size, status));
    }

    /** 标记已处理（管理员） */
    @PutMapping("/admin/reports/{id}/handle")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> handle(@PathVariable Long id, Authentication authentication) {
        reportService.handle(id, authentication.getName());
        return Result.ok();
    }
}

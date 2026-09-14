package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.ConfigValueRequest;
import com.pcdoctor.dto.SiteContentRequest;
import com.pcdoctor.dto.SiteContentView;
import com.pcdoctor.service.ConfigService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 配置接口（契约 §4.9）：QQ 群号读公开，改电医+。
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/qq-group")
    public Result<Map<String, String>> qqGroup() {
        return Result.ok(Map.of("value", configService.get(ConfigService.KEY_QQ_GROUP)));
    }

    @PutMapping("/qq-group")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public Result<Void> updateQqGroup(@Valid @RequestBody ConfigValueRequest request) {
        configService.set(ConfigService.KEY_QQ_GROUP, request.value());
        return Result.ok();
    }

    /** 站点文案（关于我们 / 使用帮助 / 交流群）：读公开，改仅管理员（需求 3） */
    @GetMapping("/site-content")
    public Result<SiteContentView> siteContent() {
        return Result.ok(configService.getSiteContent());
    }

    @PutMapping("/site-content")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SiteContentView> updateSiteContent(@Valid @RequestBody SiteContentRequest request) {
        configService.updateSiteContent(request);
        return Result.ok(configService.getSiteContent());
    }
}
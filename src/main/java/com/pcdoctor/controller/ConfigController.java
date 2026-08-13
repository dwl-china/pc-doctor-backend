package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.ConfigValueRequest;
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
}
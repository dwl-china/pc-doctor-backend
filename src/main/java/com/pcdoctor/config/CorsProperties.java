package com.pcdoctor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS 白名单配置（逗号分隔字符串 → List 绑定）。
 * 注意：列表属性不能用 @Value 占位符解析（Spring 仅对字符串值解析占位符），
 * 必须走 @ConfigurationProperties。
 */
@ConfigurationProperties(prefix = "pc-doctor.cors")
public record CorsProperties(List<String> allowedOrigins) {
}

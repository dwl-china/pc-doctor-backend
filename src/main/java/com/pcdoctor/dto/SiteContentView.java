package com.pcdoctor.dto;

import java.util.List;

/**
 * 站点文案（契约 §4.9 GET /api/config/site-content，公开）。
 * 响应统一 camelCase（与 UserView / AppointmentView 一致）；缺失的 key 回落到空串，
 * 不会因为某条文案没配置就把整个页面打成错误。
 */
public record SiteContentView(
        String aboutTitle,
        String aboutBody,
        List<FaqItem> faqItems,
        String qqNotice,
        String qqQrcode,
        // 页脚自定义 HTML；由前端消毒后渲染，服务端只做长度校验
        String footerHtml,
        // 备案号 HTML，渲染在页脚上方独立一行（与页脚分开是为了能各自单独改）
        String icpHtml) {
}

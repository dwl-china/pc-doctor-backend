package com.pcdoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 站点文案整体更新（契约 §4.9 PUT /api/config/site-content，管理员）。
 *
 * <p><b>三态语义</b>（Jackson 无法区分"字段缺失"与"显式 null"，故二者同义）：
 * <ul>
 *   <li>{@code null}（不传该字段）= 保留原值</li>
 *   <li>空串 {@code ""} = 清空该文案</li>
 *   <li>其它值 = 替换</li>
 * </ul>
 * {@code faq_items} 传 {@code []} 表示清空全部问答。
 *
 * <p>文本字段一律不加 {@code @NotBlank} —— 允许管理员清空文案。
 */
public record SiteContentRequest(
        @JsonProperty("about_title")
        @Size(max = 100, message = "标题最长 100 字")
        String aboutTitle,

        /** Markdown 正文 */
        @JsonProperty("about_body")
        @Size(max = 5000, message = "正文最长 5000 字")
        String aboutBody,

        /** 问答列表，顺序即展示顺序；上限 15 条（见 V5 迁移的 TEXT 字节上限推算） */
        @JsonProperty("faq_items")
        @Size(max = 15, message = "问答最多 15 条")
        @Valid
        List<FaqItem> faqItems,

        /** 交流群说明，可用 {qq_group} 占位符 */
        @JsonProperty("qq_notice")
        @Size(max = 500, message = "群说明最长 500 字")
        String qqNotice,

        /** 群二维码图片 URL；空串 = 清空并删除旧文件 */
        @JsonProperty("qq_qrcode")
        @Size(max = 500, message = "二维码路径过长")
        String qqQrcode) {
}

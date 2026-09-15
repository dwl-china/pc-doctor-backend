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
        String qqQrcode,

        /**
         * 页脚自定义 HTML。
         * 前端渲染前会经 DOMPurify 消毒（只放行 a/span/br 等少量标签），
         * 服务端不做 HTML 解析，只限制长度，避免把校验逻辑分散在两处。
         */
        @JsonProperty("footer_html")
        @Size(max = 1000, message = "页脚内容最长 1000 字")
        String footerHtml,

        /**
         * 备案号 HTML，渲染在页脚上方独立一行。
         * 与 {@code footer_html} 同样消毒、同样长度上限 —— 它就是「换个位置显示的页脚片段」，
         * 单独一个字段只为了能只改备案号而不动页脚。
         */
        @JsonProperty("icp_html")
        @Size(max = 1000, message = "备案号内容最长 1000 字")
        String icpHtml) {
}

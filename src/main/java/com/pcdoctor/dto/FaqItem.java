package com.pcdoctor.dto;

import jakarta.validation.constraints.Size;

/**
 * 使用帮助的一条问答（契约 §4.9 site-content）。
 * 请求与响应共用：两个字段的命名在两侧一致，无需分开定义。
 * 答案支持 Markdown，渲染端需消毒（前端 marked + DOMPurify）。
 */
public record FaqItem(
        @Size(max = 100, message = "问题最长 100 字")
        String question,

        @Size(max = 1000, message = "答案最长 1000 字")
        String answer) {
}

package com.pcdoctor.integration;

import com.pcdoctor.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 站点文案（需求 3，契约 §4.9）。
 * 种子内容由 Flyway V5 写入，因此本类同时验证 V5 迁移确实生效。
 */
class SiteContentIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("公开读取：返回 V5 种子的标题与 7 条问答")
    void publicGetReturnsSeededContent() throws Exception {
        mockMvc.perform(get("/api/config/site-content"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.aboutTitle").value("关于我们"))
                .andExpect(jsonPath("$.data.aboutBody").isNotEmpty())
                .andExpect(jsonPath("$.data.faqItems.length()").value(7))
                // 文案由 V5 种子 → V6 中性化（去掉学校/社团名，与备案名一致）
                .andExpect(jsonPath("$.data.faqItems[0].question").value("电脑硬件笔记是什么？"))
                .andExpect(jsonPath("$.data.faqItems[6].answer").isNotEmpty());
    }

    @Test
    @DisplayName("整体更新：改动回读一致，未传的字段保持原值")
    void putThenGetRoundTrip() throws Exception {
        String before = objectMapper.readTree(mockMvc.perform(get("/api/config/site-content"))
                        .andReturn().getResponse().getContentAsString())
                .at("/data/aboutBody").asString();

        // 只传标题与问答，about_body 不传 → 应保留
        mockMvc.perform(put("/api/config/site-content")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("""
                                {"about_title":"关于我们（改）",
                                 "faq_items":[{"question":"Q1","answer":"A1 **bold**"},
                                              {"question":"Q2","answer":"A2"}]}"""))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.aboutTitle").value("关于我们（改）"))
                .andExpect(jsonPath("$.data.faqItems.length()").value(2))
                .andExpect(jsonPath("$.data.aboutBody").value(before));

        mockMvc.perform(get("/api/config/site-content"))
                .andExpect(jsonPath("$.data.aboutTitle").value("关于我们（改）"))
                .andExpect(jsonPath("$.data.faqItems[0].question").value("Q1"))
                .andExpect(jsonPath("$.data.faqItems[0].answer").value("A1 **bold**"))
                .andExpect(jsonPath("$.data.faqItems[1].question").value("Q2"));
    }

    @Test
    @DisplayName("问答顺序即数组顺序，增删后条数正确")
    void faqOrderAndCountPreserved() throws Exception {
        String token = adminToken();
        mockMvc.perform(put("/api/config/site-content")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"faq_items\":[{\"question\":\"c\",\"answer\":\"3\"},"
                        + "{\"question\":\"a\",\"answer\":\"1\"},{\"question\":\"b\",\"answer\":\"2\"}]}"));

        mockMvc.perform(get("/api/config/site-content"))
                .andExpect(jsonPath("$.data.faqItems.length()").value(3))
                .andExpect(jsonPath("$.data.faqItems[0].question").value("c"))
                .andExpect(jsonPath("$.data.faqItems[1].question").value("a"))
                .andExpect(jsonPath("$.data.faqItems[2].question").value("b"));

        // 空数组 = 清空
        mockMvc.perform(put("/api/config/site-content")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"faq_items\":[]}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/config/site-content"))
                .andExpect(jsonPath("$.data.faqItems.length()").value(0));
    }

    @Test
    @DisplayName("二维码：设置后可读回；传空串清空（旧文件连带删除）")
    void qrcodeSetAndClear() throws Exception {
        String token = adminToken();
        String url = "/api/files/notice/qrcode-regression.png";

        mockMvc.perform(put("/api/config/site-content")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"qq_qrcode\":\"" + url + "\"}"))
                .andExpect(jsonPath("$.data.qqQrcode").value(url));

        mockMvc.perform(put("/api/config/site-content")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"qq_qrcode\":\"\"}"))
                .andExpect(jsonPath("$.data.qqQrcode").value(""));

        mockMvc.perform(get("/api/config/site-content"))
                .andExpect(jsonPath("$.data.qqQrcode").value(""));
    }

    @Test
    @DisplayName("页脚 HTML：备案号链接保存后可读回，且能清空")
    void footerHtmlRoundTrip() throws Exception {
        String admin = adminToken();
        String footer = "<a href=\"https://icp.gov.moe/?keyword=20261911\" target=\"_blank\">萌ICP备20261911号</a>";

        mockMvc.perform(put("/api/config/site-content")
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content("{\"footer_html\":" + objectMapper.writeValueAsString(footer) + "}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.footerHtml").value(footer));

        mockMvc.perform(get("/api/config/site-content"))
                .andExpect(jsonPath("$.data.footerHtml").value(footer));

        // 传空串清空
        mockMvc.perform(put("/api/config/site-content")
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content("{\"footer_html\":\"\"}"))
                .andExpect(jsonPath("$.data.footerHtml").value(""));
    }

    @Test
    @DisplayName("备案号与页脚互不干扰：改一个，另一个原样保留")
    void icpAndFooterAreIndependent() throws Exception {
        String admin = adminToken();
        String footer = "<a href=\"https://icp.gov.moe/?keyword=20261911\" target=\"_blank\">萌ICP备20261911号</a>";
        String icp = "<a href=\"https://beian.miit.gov.cn/\" target=\"_blank\">浙ICP备20261911号</a>";

        // 先只写页脚（模拟线上现状：页脚有内容、备案号还空着）
        mockMvc.perform(put("/api/config/site-content")
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content("{\"footer_html\":" + objectMapper.writeValueAsString(footer) + "}"))
                .andExpect(jsonPath("$.data.footerHtml").value(footer))
                .andExpect(jsonPath("$.data.icpHtml").value(""));

        // 再只写备案号 —— 页脚必须一字不动（这正是拆成两个 key 的理由）
        mockMvc.perform(put("/api/config/site-content")
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content("{\"icp_html\":" + objectMapper.writeValueAsString(icp) + "}"))
                .andExpect(jsonPath("$.data.icpHtml").value(icp))
                .andExpect(jsonPath("$.data.footerHtml").value(footer));

        mockMvc.perform(get("/api/config/site-content"))
                .andExpect(jsonPath("$.data.icpHtml").value(icp))
                .andExpect(jsonPath("$.data.footerHtml").value(footer));

        // 清空备案号：页脚仍在
        mockMvc.perform(put("/api/config/site-content")
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content("{\"icp_html\":\"\"}"))
                .andExpect(jsonPath("$.data.icpHtml").value(""))
                .andExpect(jsonPath("$.data.footerHtml").value(footer));
    }

    @Test
    @DisplayName("备案号：超长（>1000 字）→ 2001")
    void icpHtmlTooLong() throws Exception {
        mockMvc.perform(put("/api/config/site-content")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("{\"icp_html\":\"" + "x".repeat(1001) + "\"}"))
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("页脚 HTML：超长（>1000 字）→ 2001")
    void footerHtmlTooLong() throws Exception {
        mockMvc.perform(put("/api/config/site-content")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("{\"footer_html\":\"" + "x".repeat(1001) + "\"}"))
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("问答超限（>15 条）→ 2001 参数校验")
    void faqTooManyItems() throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            items.append(i > 0 ? "," : "").append("{\"question\":\"q").append(i).append("\",\"answer\":\"a\"}");
        }
        mockMvc.perform(put("/api/config/site-content")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("{\"faq_items\":[" + items + "]}"))
                .andExpect(jsonPath("$.code").value(2001));
    }
}

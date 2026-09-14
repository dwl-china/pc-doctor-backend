package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

/**
 * 站点文案配置（需求 3）：把「关于我们 / 使用帮助 / 交流群」里原本硬编码在
 * .vue 模板中的文字搬进 config_table，管理员可在管理端直接改，改文案不再需要改代码重新部署。
 *
 * 两件事：
 *   1. config_value 由 varchar(500) 放宽到 TEXT —— 正文/FAQ 远超 500 字符
 *   2. 种子 5 个 key，内容与迁移前的前端硬编码文案逐字对齐（保证上线后页面显示不变）
 *
 * 用 Java 迁移而非 SQL：FAQ 是多行 Markdown，JSON 字符串里的 \n 写成 SQL 字面量会被
 * MySQL 的反斜杠转义规则吃掉（存进去变成真的换行 → 非法 JSON），交给 Jackson 序列化最稳。
 */
public class V5__site_texts extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V5__site_texts.class);

    static final String KEY_ABOUT_TITLE = "about_title";
    static final String KEY_ABOUT_BODY = "about_body";
    static final String KEY_FAQ_ITEMS = "faq_items";
    static final String KEY_QQ_NOTICE = "qq_notice";
    static final String KEY_QQ_QRCODE = "qq_qrcode";

    @Override
    public void migrate(Context context) throws Exception {
        // 1) 放宽长度：varchar(500) → TEXT（COMMENT 必须重写，MODIFY 会抹掉原注释）
        try (PreparedStatement ps = context.getConnection().prepareStatement(
                "ALTER TABLE config_table MODIFY COLUMN config_value TEXT NOT NULL COMMENT '配置值'")) {
            ps.execute();
        }
        log.info("config_table.config_value 已放宽为 TEXT");

        // 2) 种子文案（已存在则跳过，幂等）
        seed(context, KEY_ABOUT_TITLE, "关于我们");
        seed(context, KEY_ABOUT_BODY, ABOUT_BODY);
        seed(context, KEY_FAQ_ITEMS, faqItemsJson());
        seed(context, KEY_QQ_NOTICE, QQ_NOTICE);
        seed(context, KEY_QQ_QRCODE, "");   // 空串 = 未设置二维码
    }

    private void seed(Context context, String key, String value) throws Exception {
        try (PreparedStatement check = context.getConnection()
                .prepareStatement("SELECT 1 FROM config_table WHERE config_key = ?")) {
            check.setString(1, key);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    log.info("配置 {} 已存在，跳过种子", key);
                    return;
                }
            }
        }
        try (PreparedStatement ps = context.getConnection().prepareStatement(
                "INSERT INTO config_table (config_key, config_value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
        log.info("站点文案种子已写入: {}", key);
    }

    // ------------------------------------------------------------------
    // 以下文案与迁移前 AboutView.vue / HelpView.vue 中的硬编码逐字一致
    // ------------------------------------------------------------------

    private static final String ABOUT_BODY = """
            **X计算机协会**成立于1995年，是一个面向全校、以营造校园科技文化氛围、推广计算机应用操作为宗旨的学术科技类社团。

            **电脑医院**隶属于浙江工商大学计算机协会，旨在为在校师生提供免费、专业、便捷的IT技术支援服务。""";

    /** {qq_group} 占位符由前端渲染时替换，避免群号修改后这里变成死值 */
    private static final String QQ_NOTICE = "欢迎加入电脑医院交流群：**{qq_group}**";

    private static String faqItemsJson() {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, String>> items = List.of(
                faq("电脑医院是什么？",
                        "电脑医院隶属于浙江工商大学计算机协会，旨在为在校师生提供免费、专业、便捷的IT技术支援服务。"
                                + "常见服务包括：系统问题修复、电脑清灰、病毒扫描、流氓软件清理、重装系统等。"),
                faq("如何注册账号？",
                        "在登录页切换到「注册」标签，填写账号（建议使用学号，2-50 位字母/数字/下划线）、昵称和密码（8-64 位）即可。"
                                + "建议同时填写联系方式，便于电医联系你。"),
                faq("如何提交预约？",
                        """
                        点击首页「立即预约」，按三步完成：

                        1. **问题描述**：描述你遇到的问题，并选择问题分类；
                        2. **时间地点**：选择期望的维修时间和地点（推荐选项能更快被接单）；
                        3. **图片与提交**：可上传问题截图，确认信息后提交。

                        提交后预约进入「排队中」，等待电医接单。"""),
                faq("预约后如何跟进进度？",
                        "首页「预约记录」可查看全部预约；勾选「仅查看我的记录」只看自己的单。点击卡片进入详情页，"
                                + "可通过「预约消息」与电医沟通。预约状态会在 排队中 → 处理中 → 已完成 之间流转。"),
                faq("如何修改或撤销预约？",
                        "预约处于「排队中」时，本人可在详情页撤销；已被电医接单后不可自行撤销，可在详情页发消息与电医协商。"
                                + "若需修改，请联系接单电医或管理员。"),
                faq("忘记密码或密码被重置怎么办？",
                        "联系管理员重置密码。重置后首次登录会被**强制要求修改密码**，这是正常的安全流程，修改完成后即可正常使用。"),
                faq("遇到问题如何联系我们？",
                        "可到「意见反馈」页提交反馈，或加入电脑医院交流群 **{qq_group}** 咨询。"));
        return mapper.writeValueAsString(items);
    }

    /** 用 LinkedHashMap 保证序列化后 question 在 answer 前面，存库的 JSON 便于人眼查看 */
    private static Map<String, String> faq(String question, String answer) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("question", question);
        item.put("answer", answer);
        return item;
    }
}

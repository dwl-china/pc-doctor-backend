package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

/**
 * 把站点文案里的机构名称（学校 / 社团）换成中性表述，与备案名「电脑硬件笔记」保持一致。
 *
 * <p>背景：个人 ICP 备案不能用于单位/组织性质的网站。V5 种下的「关于我们」与 FAQ 里带着
 * 学校名与社团名，管局抽查或接入商复核时可能被判定「名称与主体不符」而注销备案。
 *
 * <p><b>为什么不直接改 V5</b>：V5 已在生产库执行过，改动其内容会导致 Flyway checksum 校验失败、
 * 服务无法启动。只能新增迁移覆盖数据。
 *
 * <p><b>只改还带着旧机构名的记录</b>：如果管理员已经通过「页面文字」改过这段文案，
 * 说明那是他的新版本，这里不能覆盖。
 *
 * <p>注意：站点文案是运行时可编辑的（管理端 →「页面文字」），本迁移只负责把默认种子改中性，
 * 之后想怎么改都不需要再动代码。
 */
public class V6__neutral_about_text extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V6__neutral_about_text.class);

    /** 判定"还是旧种子"的标记：只要还含这个串就说明没被人改过 */
    private static final String LEGACY_MARKER = "浙江工商大学";

    private static final String NEW_ABOUT_BODY = """
            **电脑硬件笔记**是一个记录电脑维修经验、分享硬件知识的站点。

            提供免费、专业的电脑维修预约服务：系统故障排查、电脑清灰、病毒查杀、软件清理、系统重装等。""";

    private static final String NEW_FAQ_QUESTION = "电脑硬件笔记是什么？";
    private static final String NEW_FAQ_ANSWER =
            "电脑硬件笔记是一个记录电脑维修经验、分享硬件知识的站点，并提供免费、专业的电脑维修预约服务。"
                    + "常见服务包括：系统问题修复、电脑清灰、病毒扫描、流氓软件清理、重装系统等。";

    @Override
    public void migrate(Context context) throws Exception {
        replaceIfLegacy(context, "about_body", NEW_ABOUT_BODY);
        rewriteFaqIfLegacy(context);
    }

    /** 当前值仍含旧机构名时才覆盖（否则说明管理员改过了，保留他的版本） */
    private void replaceIfLegacy(Context context, String key, String newValue) throws Exception {
        String current = read(context, key);
        if (current == null || !current.contains(LEGACY_MARKER)) {
            log.info("配置 {} 不含旧机构名，跳过（可能已被管理员修改）", key);
            return;
        }
        try (PreparedStatement ps = context.getConnection()
                .prepareStatement("UPDATE config_table SET config_value = ? WHERE config_key = ?")) {
            ps.setString(1, newValue);
            ps.setString(2, key);
            ps.executeUpdate();
        }
        log.info("站点文案已中性化: {}", key);
    }

    /** FAQ 是 JSON 数组：把仍带旧机构名的那条问答换掉，其余原样保留 */
    private void rewriteFaqIfLegacy(Context context) throws Exception {
        String current = read(context, "faq_items");
        if (current == null || !current.contains(LEGACY_MARKER)) {
            log.info("配置 faq_items 不含旧机构名，跳过");
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> items = mapper.readValue(current, List.class);

        int replaced = 0;
        for (Map<String, Object> item : items) {
            Object answer = item.get("answer");
            if (answer instanceof String s && s.contains(LEGACY_MARKER)) {
                item.put("question", NEW_FAQ_QUESTION);
                item.put("answer", NEW_FAQ_ANSWER);
                replaced++;
            }
        }
        if (replaced == 0) {
            log.info("faq_items 中未定位到含旧机构名的问答，跳过");
            return;
        }
        try (PreparedStatement ps = context.getConnection()
                .prepareStatement("UPDATE config_table SET config_value = ? WHERE config_key = ?")) {
            ps.setString(1, mapper.writeValueAsString(items));
            ps.setString(2, "faq_items");
            ps.executeUpdate();
        }
        log.info("使用帮助问答已中性化: 替换 {} 条", replaced);
    }

    private String read(Context context, String key) throws Exception {
        try (PreparedStatement ps = context.getConnection()
                .prepareStatement("SELECT config_value FROM config_table WHERE config_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}

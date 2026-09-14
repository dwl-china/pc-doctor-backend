package com.pcdoctor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.common.TransactionAfterCommit;
import com.pcdoctor.dto.FaqItem;
import com.pcdoctor.dto.SiteContentRequest;
import com.pcdoctor.dto.SiteContentView;
import com.pcdoctor.entity.Config;
import com.pcdoctor.mapper.ConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 配置模块（#5.7）：config_table 键值表的读写。
 * <ul>
 *   <li>QQ 群号：单 key 读写（契约 §4.9）</li>
 *   <li>站点文案（需求 3）：关于我们 / 使用帮助 FAQ / 交流群说明与二维码，
 *       对外暴露为结构化对象而非扁平 key→value —— 扁平 Map 上的 {@code @Valid} 不生效，
 *       key 白名单与长度上限都会失守</li>
 * </ul>
 */
@Slf4j
@Service
public class ConfigService {

    public static final String KEY_QQ_GROUP = "qq_group";

    /** 站点文案 key（只在后端出现，前端用结构化字段名） */
    public static final String KEY_ABOUT_TITLE = "about_title";
    public static final String KEY_ABOUT_BODY = "about_body";
    public static final String KEY_FAQ_ITEMS = "faq_items";
    public static final String KEY_QQ_NOTICE = "qq_notice";
    public static final String KEY_QQ_QRCODE = "qq_qrcode";

    private static final List<String> SITE_KEYS = List.of(
            KEY_ABOUT_TITLE, KEY_ABOUT_BODY, KEY_FAQ_ITEMS, KEY_QQ_NOTICE, KEY_QQ_QRCODE);

    private final ConfigMapper configMapper;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    public ConfigService(ConfigMapper configMapper, FileService fileService, ObjectMapper objectMapper) {
        this.configMapper = configMapper;
        this.fileService = fileService;
        this.objectMapper = objectMapper;
    }

    public String get(String key) {
        Config c = configMapper.selectById(key);
        if (c == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "配置不存在");
        }
        return c.getConfigValue();
    }

    @Transactional
    public void set(String key, String value) {
        Config c = new Config();
        c.setConfigKey(key);
        c.setConfigValue(value);
        if (configMapper.selectById(key) == null) {
            configMapper.insert(c);
        } else {
            configMapper.updateById(c);
        }
        log.info("配置更新: key={}", key);
    }

    // ------------------------------------------------------------------
    // 站点文案（需求 3）
    // ------------------------------------------------------------------

    /**
     * 读取站点文案。用批量查询 + 空串兜底，**不**逐个调用 {@link #get} ——
     * 后者对缺失 key 抛 2002，任何一个 key 没配置都会把「关于我们 / 使用帮助」整页打成错误。
     */
    public SiteContentView getSiteContent() {
        Map<String, String> values = loadValues(SITE_KEYS);
        return new SiteContentView(
                values.getOrDefault(KEY_ABOUT_TITLE, ""),
                values.getOrDefault(KEY_ABOUT_BODY, ""),
                parseFaq(values.get(KEY_FAQ_ITEMS)),
                values.getOrDefault(KEY_QQ_NOTICE, ""),
                values.getOrDefault(KEY_QQ_QRCODE, ""));
    }

    /**
     * 整体更新站点文案。三态语义见 {@link SiteContentRequest}：传 null 保留、传 "" 清空、传值替换。
     *
     * <p>注意方法内的 {@code set(...)} 是自调用，会绕过 Spring 代理、其上的 {@code @Transactional}
     * 不生效 —— 靠本方法的事务兜住，不能去掉这里的注解。
     */
    @Transactional
    public void updateSiteContent(SiteContentRequest request) {
        if (request.aboutTitle() != null) {
            set(KEY_ABOUT_TITLE, request.aboutTitle());
        }
        if (request.aboutBody() != null) {
            set(KEY_ABOUT_BODY, request.aboutBody());
        }
        if (request.faqItems() != null) {
            set(KEY_FAQ_ITEMS, serializeFaq(request.faqItems()));
        }
        if (request.qqNotice() != null) {
            set(KEY_QQ_NOTICE, request.qqNotice());
        }
        if (request.qqQrcode() != null) {
            String old = loadValues(List.of(KEY_QQ_QRCODE)).getOrDefault(KEY_QQ_QRCODE, "");
            set(KEY_QQ_QRCODE, request.qqQrcode());
            if (!old.isBlank() && !old.equals(request.qqQrcode())) {
                // 换图或清空后，提交事务再删旧文件（回滚时文件还在）
                TransactionAfterCommit.run(() -> fileService.deleteManaged(old));
            }
        }
    }

    private Map<String, String> loadValues(Collection<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        List<Config> rows = configMapper.selectList(
                new LambdaQueryWrapper<Config>().in(Config::getConfigKey, keys));
        return rows.stream().collect(Collectors.toMap(Config::getConfigKey, Config::getConfigValue));
    }

    /** FAQ 存库为 JSON 数组；解析失败时降级为空列表并告警，不让脏数据打挂页面 */
    private List<FaqItem> parseFaq(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(json, FaqItem[].class));
        } catch (Exception e) {
            log.warn("faq_items 配置不是合法 JSON，已按空列表处理: {}", e.getMessage());
            return List.of();
        }
    }

    private String serializeFaq(List<FaqItem> items) {
        return objectMapper.writeValueAsString(items);
    }
}

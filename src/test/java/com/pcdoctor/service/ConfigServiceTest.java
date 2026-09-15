package com.pcdoctor.service;

import com.pcdoctor.entity.Config;
import com.pcdoctor.mapper.ConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站点文案服务（需求 3）。
 * 最关键的一条：读取时遇到没配置的 key 必须回落空串，**不能**抛 2002 ——
 * 原 {@code get()} 对缺失 key 抛错，若按 key 循环调用会把「关于我们 / 使用帮助」整页打成错误。
 */
@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    ConfigMapper configMapper;
    @Mock
    FileService fileService;

    ConfigService service;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ConfigService(configMapper, fileService, MAPPER);
    }

    @Test
    @DisplayName("配置全缺失：返回空串与空问答列表，不抛异常")
    void missingKeysFallBackToEmpty() {
        when(configMapper.selectList(any())).thenReturn(List.of());

        var view = service.getSiteContent();

        assertThat(view.aboutTitle()).isEmpty();
        assertThat(view.aboutBody()).isEmpty();
        assertThat(view.qqNotice()).isEmpty();
        assertThat(view.qqQrcode()).isEmpty();
        assertThat(view.faqItems()).isEmpty();
    }

    @Test
    @DisplayName("部分缺失：有值的返回原值，缺的回落空串")
    void partiallyConfigured() {
        when(configMapper.selectList(any())).thenReturn(List.of(
                config(ConfigService.KEY_ABOUT_TITLE, "关于我们"),
                config(ConfigService.KEY_QQ_QRCODE, "/api/files/notice/qr.png")));

        var view = service.getSiteContent();

        assertThat(view.aboutTitle()).isEqualTo("关于我们");
        assertThat(view.qqQrcode()).isEqualTo("/api/files/notice/qr.png");
        assertThat(view.aboutBody()).isEmpty();
    }

    @Test
    @DisplayName("问答 JSON 解析：顺序与内容保持一致")
    void faqParsedInOrder() {
        when(configMapper.selectList(any())).thenReturn(List.of(config(ConfigService.KEY_FAQ_ITEMS,
                "[{\"question\":\"q1\",\"answer\":\"a1\"},{\"question\":\"q2\",\"answer\":\"a2\"}]")));

        var view = service.getSiteContent();

        assertThat(view.faqItems()).hasSize(2);
        assertThat(view.faqItems().get(0).question()).isEqualTo("q1");
        assertThat(view.faqItems().get(1).answer()).isEqualTo("a2");
    }

    @Test
    @DisplayName("问答 JSON 损坏：降级为空列表，不把页面打挂")
    void faqCorruptedDegradesToEmpty() {
        when(configMapper.selectList(any())).thenReturn(List.of(
                config(ConfigService.KEY_FAQ_ITEMS, "{不是合法 JSON")));

        var view = service.getSiteContent();

        assertThat(view.faqItems()).isEmpty();
    }

    @Test
    @DisplayName("更新：只写传入的字段，未传的字段不落库")
    void updateWritesOnlyProvidedFields() {
        service.updateSiteContent(new com.pcdoctor.dto.SiteContentRequest(
                "新标题", null, null, null, null, null));

        // 只有一次写入，且就是传入的那个 key（其余 4 个 key 不该被碰）
        ArgumentCaptor<Config> captor = ArgumentCaptor.forClass(Config.class);
        verify(configMapper).insert(captor.capture());
        assertThat(captor.getValue().getConfigKey()).isEqualTo(ConfigService.KEY_ABOUT_TITLE);
        assertThat(captor.getValue().getConfigValue()).isEqualTo("新标题");
    }

    @Test
    @DisplayName("更新二维码：换图时删除旧文件")
    void updateQrcodeDeletesOldFile() {
        when(configMapper.selectList(any())).thenReturn(List.of(
                config(ConfigService.KEY_QQ_QRCODE, "/api/files/notice/old.png")));

        service.updateSiteContent(new com.pcdoctor.dto.SiteContentRequest(
                null, null, null, null, "/api/files/notice/new.png", null));

        verify(fileService).deleteManaged("/api/files/notice/old.png");
    }

    @Test
    @DisplayName("清空二维码：同样删除旧文件")
    void clearQrcodeDeletesOldFile() {
        when(configMapper.selectList(any())).thenReturn(List.of(
                config(ConfigService.KEY_QQ_QRCODE, "/api/files/notice/old.png")));

        service.updateSiteContent(new com.pcdoctor.dto.SiteContentRequest(
                null, null, null, null, "", null));

        verify(fileService).deleteManaged("/api/files/notice/old.png");
    }

    @Test
    @DisplayName("未传二维码：不动文件也不写库")
    void untouchedQrcodeKeepsFile() {
        service.updateSiteContent(new com.pcdoctor.dto.SiteContentRequest(
                null, null, null, null, null, null));

        verify(fileService, never()).deleteManaged(any());
        verify(configMapper, never()).insert(any(Config.class));
    }

    private Config config(String key, String value) {
        Config c = new Config();
        c.setConfigKey(key);
        c.setConfigValue(value);
        return c;
    }
}

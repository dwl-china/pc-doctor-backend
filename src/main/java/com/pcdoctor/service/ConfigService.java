package com.pcdoctor.service;

import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.entity.Config;
import com.pcdoctor.mapper.ConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配置模块（#5.7）：QQ 群号读写（config_table 键值表）。
 */
@Slf4j
@Service
public class ConfigService {

    public static final String KEY_QQ_GROUP = "qq_group";

    private final ConfigMapper configMapper;

    public ConfigService(ConfigMapper configMapper) {
        this.configMapper = configMapper;
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
}
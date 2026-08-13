package com.pcdoctor.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 键值配置表 config_table（老库 notice_table 规范化而来）。
 */
@Data
@TableName("config_table")
public class Config {

    @TableId
    private String configKey;

    private String configValue;

    private LocalDateTime updateTime;
}
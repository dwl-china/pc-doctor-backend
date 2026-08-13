package com.pcdoctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动表 activity_table。
 */
@Data
@TableName("activity_table")
public class Activity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String summary;

    /** 详情文件路径 */
    private String file;

    /** 封面图路径 */
    private String cover;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
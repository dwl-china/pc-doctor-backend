package com.pcdoctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问题分类表 category_table（老库无主键，已规范化加自增 id，#34）。
 */
@Data
@TableName("category_table")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private LocalDateTime createTime;
}
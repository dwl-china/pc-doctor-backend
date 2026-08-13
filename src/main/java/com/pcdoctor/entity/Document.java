package com.pcdoctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档表 document_table。
 */
@Data
@TableName("document_table")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String summary;

    /** Markdown 文件路径（/api/files/doc/xxx.md） */
    private String file;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
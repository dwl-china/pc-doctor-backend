package com.pcdoctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预约留言表 message_table，字段与 V1__init.sql 对齐。
 */
@Data
@TableName("message_table")
public class AppointmentMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属预约 */
    private Long appointmentId;

    /** 留言用户（老库 user_name 实为登录账号，已规范化） */
    private String userId;

    private String message;

    private String picture;

    private LocalDateTime createTime;
}
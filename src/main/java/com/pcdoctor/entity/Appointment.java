package com.pcdoctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预约表 appointment_table，字段与 V1__init.sql 对齐。
 */
@Data
@TableName("appointment_table")
public class Appointment {

    public static final int STATUS_QUEUED = 0;
    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_DONE = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起预约的用户 */
    private String userId;

    private String problemDescription;

    private Long categoryId;

    private String problemPicture;

    /** 用户期望时间（自由文本） */
    private String availableTime;

    private String appointmentLocation;

    /** 0=排队 1=处理中 2=已完成 */
    private Integer status;

    /** 接单电医 */
    private String doctorId;

    private LocalDateTime createTime;

    /** 接单时间（0→1 时写入） */
    private LocalDateTime appointmentTime;

    /** 完成时间（1→2 时写入） */
    private LocalDateTime doneTime;

    private LocalDateTime updateTime;
}
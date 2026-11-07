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

    /** 0=待处理 2=已处理（接单即处理完成，不再有中间状态） */
    private Integer status;

    /** 接单电医（管理员接单时也可能是管理员账号） */
    private String doctorId;

    private LocalDateTime createTime;

    /** 接单时间（接单即完成，与 doneTime 同时写入） */
    private LocalDateTime appointmentTime;

    /** 处理完成时间 */
    private LocalDateTime doneTime;

    private LocalDateTime updateTime;
}
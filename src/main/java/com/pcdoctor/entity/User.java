package com.pcdoctor.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表 user_table，字段与 V1__init.sql 对齐。
 */
@Data
@TableName("user_table")
public class User {

    /** 登录账号（学号等），主键 */
    @TableId
    private String userId;

    private String userName;

    /** male / female / unknown */
    private String sex;

    /** bcrypt 哈希，明文永不落库 */
    private String passwordHash;

    /** 0=普通用户 1=电医 2=管理员 */
    private Integer level;

    /** 1=下次登录强制改密（迁移用户/管理员重置后） */
    private Integer mustChangePassword;

    private String contactDetails;

    private String userDescription;

    private String userPicture;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
-- V1__init.sql
-- PC-Doctor 规范化 schema（对应《项目分析报告》#34）
-- 约定：全部 utf8mb4 + InnoDB；level/status 用 tinyint；时间统一 datetime；
--       user_id 沿用老库的 varchar 登录名作主键；notice_table 改为 config_table 键值表。

CREATE TABLE user_table
(
    user_id              varchar(50)  NOT NULL COMMENT '登录账号（学号等）',
    user_name            varchar(50)  NOT NULL COMMENT '昵称',
    sex                  varchar(10)  NOT NULL DEFAULT 'unknown' COMMENT 'male / female / unknown',
    password_hash        varchar(100) NOT NULL COMMENT 'bcrypt 哈希，明文永不落库',
    level                tinyint      NOT NULL DEFAULT 0 COMMENT '0=普通用户 1=电医 2=管理员',
    must_change_password tinyint      NOT NULL DEFAULT 0 COMMENT '1=下次登录强制改密（迁移用户/管理员重置后）',
    contact_details      varchar(255) NOT NULL DEFAULT '' COMMENT '联系方式',
    user_description     varchar(255) NULL COMMENT '个人简介',
    user_picture         varchar(500) NULL COMMENT '头像文件路径',
    create_time          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    update_time          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';

CREATE TABLE category_table
(
    id          bigint unsigned NOT NULL AUTO_INCREMENT,
    name        varchar(50)     NOT NULL COMMENT '分类名',
    create_time datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '问题分类表';

CREATE TABLE appointment_table
(
    id                   bigint unsigned NOT NULL AUTO_INCREMENT,
    user_id              varchar(50)     NOT NULL COMMENT '发起预约的用户',
    problem_description  text            NOT NULL COMMENT '问题描述',
    category_id          bigint unsigned NOT NULL COMMENT '问题分类（category_table.id）',
    problem_picture      varchar(500)    NULL COMMENT '问题图片',
    available_time       varchar(255)    NULL COMMENT '用户期望时间（自由文本）',
    appointment_location varchar(255)    NULL COMMENT '预约地点',
    status               tinyint         NOT NULL DEFAULT 0 COMMENT '0=排队 1=处理中 2=已完成',
    doctor_id            varchar(50)     NULL COMMENT '接单电医',
    create_time          datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预约发起时间',
    appointment_time     datetime        NULL COMMENT '接单时间（0→1 时写入）',
    done_time            datetime        NULL COMMENT '完成时间（1→2 时写入）',
    update_time          datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_appointment_user (user_id),
    KEY idx_appointment_doctor (doctor_id),
    KEY idx_appointment_status (status),
    CONSTRAINT fk_appointment_user FOREIGN KEY (user_id) REFERENCES user_table (user_id),
    CONSTRAINT fk_appointment_doctor FOREIGN KEY (doctor_id) REFERENCES user_table (user_id),
    CONSTRAINT fk_appointment_category FOREIGN KEY (category_id) REFERENCES category_table (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '预约表';

CREATE TABLE message_table
(
    id             bigint unsigned NOT NULL AUTO_INCREMENT,
    appointment_id bigint unsigned NOT NULL COMMENT '所属预约',
    user_id        varchar(50)     NOT NULL COMMENT '留言用户',
    message        text            NOT NULL COMMENT '留言内容',
    picture        varchar(500)    NULL COMMENT '留言图片',
    create_time    datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '留言时间',
    PRIMARY KEY (id),
    KEY idx_message_appointment (appointment_id),
    CONSTRAINT fk_message_appointment FOREIGN KEY (appointment_id) REFERENCES appointment_table (id),
    CONSTRAINT fk_message_user FOREIGN KEY (user_id) REFERENCES user_table (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '预约留言表';

CREATE TABLE document_table
(
    id          bigint unsigned NOT NULL AUTO_INCREMENT,
    title       varchar(255)    NOT NULL COMMENT '标题',
    summary     varchar(500)    NULL COMMENT '摘要',
    file        varchar(255)    NOT NULL COMMENT 'Markdown 文件路径',
    create_time datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '文档表';

CREATE TABLE activity_table
(
    id          bigint unsigned NOT NULL AUTO_INCREMENT,
    title       varchar(255)    NOT NULL COMMENT '活动标题',
    summary     varchar(255)    NOT NULL COMMENT '活动简介',
    file        varchar(255)    NOT NULL COMMENT '详情文件路径',
    cover       varchar(500)    NULL COMMENT '封面图',
    create_time datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '活动表';

CREATE TABLE config_table
(
    config_key   varchar(100) NOT NULL COMMENT '配置键',
    config_value varchar(500) NOT NULL COMMENT '配置值',
    update_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (config_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '键值配置表（老库 notice_table 规范化而来）';
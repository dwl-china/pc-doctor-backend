package com.pcdoctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容举报（走查建议 #3）。
 * target_type：1=预约 2=留言；status：0=待处理 1=已处理。
 */
@Data
@TableName("report_table")
public class Report {

    /** 举报对象类型 */
    public static final int TYPE_APPOINTMENT = 1;
    public static final int TYPE_MESSAGE = 2;
    /** 处理状态 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_HANDLED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer targetType;
    private Long targetId;
    private String reason;
    private String reporterId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
    private String handlerId;
}

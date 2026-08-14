package com.pcdoctor.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.pcdoctor.dto.ReportView;
import com.pcdoctor.entity.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReportMapper extends BaseMapper<Report> {

    /** 管理端列表：联表带出举报目标快照（预约描述 / 留言内容），筛选条件由 wrapper 控制 */
    @Select("""
            SELECT r.id, r.target_type, r.target_id, r.reason, r.reporter_id, r.status,
                   r.create_time, r.handle_time, r.handler_id,
                   CASE WHEN r.target_type = 1 THEN a.problem_description
                        WHEN r.target_type = 2 THEN m.message
                        ELSE NULL END AS target_summary
            FROM report_table r
            LEFT JOIN appointment_table a ON r.target_type = 1 AND a.id = r.target_id
            LEFT JOIN message_table m ON r.target_type = 2 AND m.id = r.target_id
            ${ew.customSqlSegment}
            """)
    IPage<ReportView> selectPageWithSnapshot(IPage<?> page, @Param(Constants.WRAPPER) Wrapper<Report> wrapper);
}


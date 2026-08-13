package com.pcdoctor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.pcdoctor.dto.AppointmentView;
import com.pcdoctor.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {

    /**
     * 预约分页 + 联表（发单人/电医昵称、分类名）。筛选条件与排序由 wrapper 控制，
     * 排序字符串为固定常量（.last），无用户输入拼接（#1）。
     */
    @Select("""
            SELECT a.id, a.user_id, u.user_name, a.problem_description, a.category_id, c.name AS category_name,
                   a.problem_picture, a.available_time, a.appointment_location, a.status,
                   a.doctor_id, d.user_name AS doctor_name,
                   a.create_time, a.appointment_time, a.done_time
            FROM appointment_table a
            LEFT JOIN user_table u ON u.user_id = a.user_id
            LEFT JOIN user_table d ON d.user_id = a.doctor_id
            LEFT JOIN category_table c ON c.id = a.category_id
            ${ew.customSqlSegment}
            """)
    IPage<AppointmentView> selectPageWithDetails(IPage<?> page, @Param(Constants.WRAPPER) com.baomidou.mybatisplus.core.conditions.Wrapper<Appointment> wrapper);
}
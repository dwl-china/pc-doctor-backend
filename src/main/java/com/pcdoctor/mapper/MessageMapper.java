package com.pcdoctor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcdoctor.dto.MessageView;
import com.pcdoctor.entity.AppointmentMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<AppointmentMessage> {

    /** 留言列表 + 留言人昵称（契约：留言按 user_id 关联，#34） */
    @Select("""
            SELECT m.id, m.appointment_id, m.user_id, u.user_name, m.message, m.picture, m.create_time
            FROM message_table m
            LEFT JOIN user_table u ON u.user_id = m.user_id
            WHERE m.appointment_id = #{appointmentId}
            ORDER BY m.create_time
            """)
    List<MessageView> selectViewsByAppointment(@Param("appointmentId") Long appointmentId);
}
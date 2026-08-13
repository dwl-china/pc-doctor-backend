package com.pcdoctor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcdoctor.dto.DoctorStatView;
import com.pcdoctor.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 电医列表 + 接单统计（u.user_id 为主键，MySQL 8 函数依赖允许只按主键分组） */
    @Select("""
            SELECT u.user_id, u.user_name, u.sex, u.contact_details, u.user_description,
                   u.user_picture, u.create_time,
                   COUNT(CASE WHEN a.status >= 1 THEN 1 END) AS accepted_count,
                   COUNT(CASE WHEN a.status = 2 THEN 1 END) AS completed_count
            FROM user_table u
            LEFT JOIN appointment_table a ON a.doctor_id = u.user_id
            WHERE u.level = 1
            GROUP BY u.user_id
            ORDER BY u.user_id
            """)
    IPage<DoctorStatView> selectDoctorStats(IPage<?> page);
}
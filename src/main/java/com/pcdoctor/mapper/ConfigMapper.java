package com.pcdoctor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcdoctor.entity.Config;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConfigMapper extends BaseMapper<Config> {
}
package com.a05.aiinterview.position.mapper;

import com.a05.aiinterview.position.entity.PositionSkillDomain;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 岗位知识域 Mapper，继承 MyBatis-Plus BaseMapper 获得基础 CRUD 能力。
 */
@Mapper
public interface PositionSkillDomainMapper extends BaseMapper<PositionSkillDomain> {
}

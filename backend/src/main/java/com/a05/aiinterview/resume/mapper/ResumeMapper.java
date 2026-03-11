package com.a05.aiinterview.resume.mapper;

import com.a05.aiinterview.resume.entity.Resume;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 简历表 Mapper。
 */
@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {
}

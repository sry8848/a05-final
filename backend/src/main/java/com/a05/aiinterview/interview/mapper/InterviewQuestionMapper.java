package com.a05.aiinterview.interview.mapper;

import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面试题目 Mapper。
 */
@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {
}

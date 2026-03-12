package com.a05.aiinterview.interview.mapper;

import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 面试题目 Mapper。
 */
@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {

    /**
     * 查询用户历史每场首题 INTRO 记录（按时间倒序），支持 limit。
     *
     * @param userId 用户 ID
     * @param limit  返回条数，null 表示不限制
     */
    List<InterviewQuestion> selectUserFirstIntroQuestions(@Param("userId") Long userId,
                                                           @Param("limit") Integer limit);
}

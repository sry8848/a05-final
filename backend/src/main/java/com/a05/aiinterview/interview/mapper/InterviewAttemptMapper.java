package com.a05.aiinterview.interview.mapper;

import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 回答尝试 Mapper。
 */
@Mapper
public interface InterviewAttemptMapper extends BaseMapper<InterviewAttempt> {

    /**
     * 根据幂等键查询历史回答记录。
     *
     * @param attemptId 客户端生成的唯一幂等键
     * @return 若已存在则返回记录，否则返回 null
     */
    InterviewAttempt selectByAttemptId(@Param("attemptId") String attemptId);
}

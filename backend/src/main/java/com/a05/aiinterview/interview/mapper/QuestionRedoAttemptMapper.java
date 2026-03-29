package com.a05.aiinterview.interview.mapper;

import com.a05.aiinterview.interview.entity.QuestionRedoAttempt;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 单题重答记录 Mapper。
 */
@Mapper
public interface QuestionRedoAttemptMapper extends BaseMapper<QuestionRedoAttempt> {

    /**
     * 查询用户在某道原题上的最新重答记录。
     */
    QuestionRedoAttempt selectLatestBySource(@Param("userId") Long userId,
                                             @Param("sourceSessionId") Long sourceSessionId,
                                             @Param("sourceQuestionId") Long sourceQuestionId);
}

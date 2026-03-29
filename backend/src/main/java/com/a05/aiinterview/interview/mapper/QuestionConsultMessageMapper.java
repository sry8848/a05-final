package com.a05.aiinterview.interview.mapper;

import com.a05.aiinterview.interview.entity.QuestionConsultMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 单题追问消息 Mapper。
 */
@Mapper
public interface QuestionConsultMessageMapper extends BaseMapper<QuestionConsultMessage> {

    List<QuestionConsultMessage> selectConversation(@Param("userId") Long userId,
                                                    @Param("sessionId") Long sessionId,
                                                    @Param("questionId") Long questionId);

    QuestionConsultMessage selectLatestGenerating(@Param("userId") Long userId,
                                                  @Param("sessionId") Long sessionId,
                                                  @Param("questionId") Long questionId);
}

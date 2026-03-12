package com.a05.aiinterview.interview.mapper;

import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select({
            "<script>",
            "SELECT q.*",
            "FROM interview_questions q",
            "INNER JOIN interview_sessions s ON s.id = q.session_id",
            "WHERE s.user_id = #{userId}",
            "  AND q.question_no = 1",
            "  AND q.question_type = 'INTRO'",
            "ORDER BY q.created_at DESC, q.id DESC",
            "<if test='limit != null'>",
            "LIMIT #{limit}",
            "</if>",
            "</script>"
    })
    List<InterviewQuestion> selectUserFirstIntroQuestions(@Param("userId") Long userId,
                                                           @Param("limit") Integer limit);
}

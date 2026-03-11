package com.a05.aiinterview.interview.mapper;

import com.a05.aiinterview.interview.entity.InterviewReport;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 面试报告 Mapper。
 */
@Mapper
public interface InterviewReportMapper extends BaseMapper<InterviewReport> {

    /**
     * 根据会话 ID 查询报告（session_id 唯一键）。
     *
     * @param sessionId 面试会话 ID
     * @return 报告实体，若未生成则返回 null
     */
    @Select("SELECT * FROM interview_reports WHERE session_id = #{sessionId} LIMIT 1")
    InterviewReport selectBySessionId(Long sessionId);
}

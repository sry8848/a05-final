package com.a05.aiinterview.interview.mapper;

import com.a05.aiinterview.interview.entity.InterviewSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 面试会话 Mapper。
 */
@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {

    /**
     * 以排他行锁方式查询会话（SELECT ... FOR UPDATE）。
     * 在 @Transactional 方法中调用，保证同一 sessionId 的账本 Patch 串行执行，避免并发覆写。
     *
     * @param sessionId 面试会话 ID
     * @return 加锁的会话实体
     */
    @Select("SELECT * FROM interview_sessions WHERE id = #{sessionId} FOR UPDATE")
    InterviewSession selectForUpdate(Long sessionId);
}

package com.a05.aiinterview.interview.mapper;

import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.dto.InterviewHistoryItemDto;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

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
    InterviewSession selectForUpdate(@Param("sessionId") Long sessionId);

    List<InterviewHistoryItemDto> selectHistoryPage(@Param("userId") Long userId,
                                                    @Param("status") String status,
                                                    @Param("targetRole") String targetRole,
                                                    @Param("dateFrom") LocalDateTime dateFrom,
                                                    @Param("dateTo") LocalDateTime dateTo,
                                                    @Param("sortBy") String sortBy,
                                                    @Param("sortOrder") String sortOrder,
                                                    @Param("offset") Integer offset,
                                                    @Param("limit") Integer limit);

    Long countHistory(@Param("userId") Long userId,
                      @Param("status") String status,
                      @Param("targetRole") String targetRole,
                      @Param("dateFrom") LocalDateTime dateFrom,
                      @Param("dateTo") LocalDateTime dateTo);

    List<InterviewSession> selectPlannerRecentSessions(@Param("userId") Long userId,
                                                       @Param("targetRole") String targetRole,
                                                       @Param("statuses") List<String> statuses,
                                                       @Param("dateFrom") LocalDateTime dateFrom,
                                                       @Param("excludeSessionId") Long excludeSessionId,
                                                       @Param("limit") Integer limit);
}

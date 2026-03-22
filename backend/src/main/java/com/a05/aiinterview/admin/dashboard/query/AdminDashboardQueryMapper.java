package com.a05.aiinterview.admin.dashboard.query;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminDashboardQueryMapper {

    Long countTotalUsers();

    Long countNewUsersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Long countTotalInterviews();

    Long countInterviewsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Long countActiveInterviews();

    Long sumTokensBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<DailyMetricRow> selectDailyNewUsers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<DailyMetricRow> selectDailyInterviews(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<DailyMetricRow> selectDailyTokens(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<ModelStatusRow> selectModelStatuses(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<ModelLatencySampleRow> selectModelLatencySamples(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<PromptUsageRow> selectPromptUsages(@Param("recentStart") LocalDateTime recentStart, @Param("end") LocalDateTime end);
}

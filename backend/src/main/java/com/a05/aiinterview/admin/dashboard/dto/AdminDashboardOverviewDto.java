package com.a05.aiinterview.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardOverviewDto {

    private Long totalUsers;
    private Long newUsersToday;
    private Long totalInterviews;
    private Long interviewsToday;
    private Long activeInterviews;
    private Long totalTokensToday;
}

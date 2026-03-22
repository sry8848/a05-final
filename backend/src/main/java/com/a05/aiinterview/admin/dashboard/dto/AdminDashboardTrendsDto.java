package com.a05.aiinterview.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardTrendsDto {

    private List<String> dates;
    private List<Long> newUsers;
    private List<Long> interviews;
    private List<Long> totalTokens;
}

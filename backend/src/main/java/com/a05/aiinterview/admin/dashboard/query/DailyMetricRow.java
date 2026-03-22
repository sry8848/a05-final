package com.a05.aiinterview.admin.dashboard.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyMetricRow {

    private LocalDate metricDate;
    private Long metricValue;
}

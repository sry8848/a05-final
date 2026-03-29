package com.a05.aiinterview.admin.dashboard.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelStatusRow {

    private String modelProvider;
    private String modelName;
    private Long requestCount;
    private Long successCount;
    private Long errorCount;
    private Long avgLatencyMs;
}

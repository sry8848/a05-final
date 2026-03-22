package com.a05.aiinterview.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardPromptSummaryDto {

    private String promptCode;
    private String configuredVersion;
    private String templateVersion;
    private String sourcePath;
    private String lastUsedAt;
    private Long callsLast24Hours;
}

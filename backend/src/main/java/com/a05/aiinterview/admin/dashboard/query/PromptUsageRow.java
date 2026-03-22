package com.a05.aiinterview.admin.dashboard.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromptUsageRow {

    private String promptCode;
    private Long callsLast24Hours;
    private LocalDateTime lastUsedAt;
}

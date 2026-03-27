package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "历史面试条目")
public class InterviewHistoryItemDto {

    private Long sessionId;
    private String title;
    private String positionCode;
    private String mode;
    private BigDecimal overallScore;
    private Integer questionCount;
    private String status;
    private LocalDateTime createdAt;
}

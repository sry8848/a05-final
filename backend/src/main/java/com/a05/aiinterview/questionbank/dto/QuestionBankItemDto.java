package com.a05.aiinterview.questionbank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "问答库条目")
public class QuestionBankItemDto {

    private Long id;
    private Long questionId;
    private Long sessionId;
    private BigDecimal score;
    private String tag;
    private String questionStem;
    private String domainName;
    private String questionType;
    private String answerSummary;
    private String sourceCreatedAt;
    private String createdAt;
}

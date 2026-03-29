package com.a05.aiinterview.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单题追问 AI 入参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionConsultInput {

    private Long interviewId;
    private Long questionId;
    private Long assistantMessageId;

    private String positionCode;
    private String experienceLevel;
    private String mode;

    private String questionStem;
    private String questionType;
    private String domainCode;
    private String domainName;

    private String originalAnswerText;

    private BigDecimal evaluationScore;
    private String evaluationCommentary;
    private List<String> strengthPoints;
    private List<String> weakPoints;
    private List<String> idealAnswerOutline;
    private String rewrittenAnswer;

    private List<ConsultTurn> consultHistory;
    private String latestUserQuestion;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsultTurn {
        private String role;
        private String content;
    }
}

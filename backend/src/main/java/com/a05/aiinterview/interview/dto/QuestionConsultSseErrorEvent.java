package com.a05.aiinterview.interview.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 单题追问 SSE error 事件。
 */
@Data
@Builder
public class QuestionConsultSseErrorEvent {

    private Long assistantMessageId;

    private String message;

    private String status;
}

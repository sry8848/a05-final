package com.a05.aiinterview.interview.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 单题追问 SSE start 事件。
 */
@Data
@Builder
public class QuestionConsultSseStartEvent {

    private Long assistantMessageId;
}

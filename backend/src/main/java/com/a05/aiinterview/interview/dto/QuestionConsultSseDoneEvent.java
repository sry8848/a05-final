package com.a05.aiinterview.interview.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 单题追问 SSE done 事件。
 */
@Data
@Builder
public class QuestionConsultSseDoneEvent {

    private Long assistantMessageId;

    private String content;

    private String status;
}

package com.a05.aiinterview.interview.dto;

import lombok.Data;

/**
 * 创建单题追问消息响应。
 */
@Data
public class CreateQuestionConsultMessageResponse {

    private Long userMessageId;

    private Long assistantMessageId;
}

package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单题追问消息 DTO。
 */
@Data
@Schema(description = "单题追问消息")
public class QuestionConsultMessageDto {

    private Long id;

    private String role;

    private String status;

    private String content;

    private Long replyToMessageId;

    private LocalDateTime createdAt;
}

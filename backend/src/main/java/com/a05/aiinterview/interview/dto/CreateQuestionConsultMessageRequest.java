package com.a05.aiinterview.interview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建单题追问消息请求。
 */
@Data
public class CreateQuestionConsultMessageRequest {

    @NotBlank(message = "追问内容不能为空")
    private String content;
}

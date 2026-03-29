package com.a05.aiinterview.questionbank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "问答库分页结果")
public class QuestionBankPageDto {

    private long total;
    private int page;
    private int pageSize;
    private List<QuestionBankItemDto> items;
}

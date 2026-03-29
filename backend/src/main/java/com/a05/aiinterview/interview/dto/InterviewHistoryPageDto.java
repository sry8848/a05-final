package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "历史面试分页响应")
public class InterviewHistoryPageDto {

    private long total;
    private int page;
    private int pageSize;
    private List<InterviewHistoryItemDto> items;
}

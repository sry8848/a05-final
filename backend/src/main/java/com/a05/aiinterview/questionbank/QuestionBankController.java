package com.a05.aiinterview.questionbank;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.questionbank.dto.QuestionBankCreateRequest;
import com.a05.aiinterview.questionbank.dto.QuestionBankItemDto;
import com.a05.aiinterview.questionbank.dto.QuestionBankPageDto;
import com.a05.aiinterview.questionbank.service.QuestionBankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Tag(name = "Question Bank")
@RestController
@RequestMapping("/question-bank")
@RequiredArgsConstructor
public class QuestionBankController {

    private final QuestionBankService questionBankService;

    @Operation(summary = "收藏到问答库")
    @PostMapping
    public ApiResponse<QuestionBankItemDto> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody QuestionBankCreateRequest request) {
        return ApiResponse.ok(questionBankService.collect(userId, request));
    }

    @Operation(summary = "获取问答库列表")
    @GetMapping
    public ApiResponse<QuestionBankPageDto> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "minScore", required = false) BigDecimal minScore,
            @RequestParam(value = "maxScore", required = false) BigDecimal maxScore,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        return ApiResponse.ok(questionBankService.list(userId, page, pageSize, tag, minScore, maxScore, sortBy, sortOrder));
    }

    @Operation(summary = "删除问答库条目")
    @DeleteMapping("/{itemId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long itemId) {
        questionBankService.delete(userId, itemId);
        return ApiResponse.ok(null);
    }
}

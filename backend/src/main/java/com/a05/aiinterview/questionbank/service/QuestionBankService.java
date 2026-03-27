package com.a05.aiinterview.questionbank.service;

import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.service.support.InterviewDomainDisplaySupport;
import com.a05.aiinterview.questionbank.dto.QuestionBankCreateRequest;
import com.a05.aiinterview.questionbank.dto.QuestionBankItemDto;
import com.a05.aiinterview.questionbank.dto.QuestionBankPageDto;
import com.a05.aiinterview.questionbank.entity.QuestionBankItem;
import com.a05.aiinterview.questionbank.mapper.QuestionBankItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionBankService {

    private final QuestionBankItemMapper questionBankItemMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;

    public QuestionBankItemDto collect(Long userId, QuestionBankCreateRequest request) {
        InterviewSession session = interviewSessionMapper.selectById(request.getSessionId());
        if (session == null || !session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("面试会话不存在或无权访问");
        }

        InterviewQuestion question = interviewQuestionMapper.selectById(request.getQuestionId());
        if (question == null || !question.getSessionId().equals(request.getSessionId())) {
            throw new IllegalArgumentException("题目不存在或不属于该会话");
        }

        QuestionBankItem existing = questionBankItemMapper.selectOne(new LambdaQueryWrapper<QuestionBankItem>()
                .eq(QuestionBankItem::getUserId, userId)
                .eq(QuestionBankItem::getSessionId, request.getSessionId())
                .eq(QuestionBankItem::getQuestionId, request.getQuestionId())
                .last("LIMIT 1"));
        if (existing != null) {
            return toDto(existing);
        }

        InterviewAttempt latestAttempt = interviewAttemptMapper.selectLatestFinalAttempt(request.getSessionId(), request.getQuestionId());
        BigDecimal score = readScore(latestAttempt);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("questionStem", question.getStem());
        snapshot.put("domainCode", resolveDomainCode(question));
        snapshot.put("domainName", resolveDomainName(session, question));
        snapshot.put("questionType", question.getQuestionType());
        snapshot.put("focusPoint", resolveFocusPoint(question));
        snapshot.put("answerSummary", summarizeAnswer(latestAttempt != null ? latestAttempt.getAnswerText() : null));
        snapshot.put("sourceCreatedAt", session.getCreatedAt() != null ? session.getCreatedAt().toString() : null);

        QuestionBankItem item = new QuestionBankItem();
        item.setUserId(userId);
        item.setSessionId(request.getSessionId());
        item.setQuestionId(request.getQuestionId());
        item.setDomainCode(resolveDomainCode(question));
        item.setScore(score);
        item.setTag(StringUtils.hasText(request.getTag()) ? request.getTag().trim() : null);
        item.setSourceSnapshotJson(snapshot);
        item.setCreatedAt(LocalDateTime.now());
        questionBankItemMapper.insert(item);

        return toDto(item);
    }

    public QuestionBankPageDto list(Long userId,
                                    int page,
                                    int pageSize,
                                    String tag,
                                    BigDecimal minScore,
                                    BigDecimal maxScore,
                                    String sortBy,
                                    String sortOrder) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safePageSize;

        LambdaQueryWrapper<QuestionBankItem> countWrapper = buildListWrapper(userId, tag, minScore, maxScore);
        long total = questionBankItemMapper.selectCount(countWrapper);

        LambdaQueryWrapper<QuestionBankItem> listWrapper = buildListWrapper(userId, tag, minScore, maxScore);
        applySort(listWrapper, sortBy, sortOrder);
        listWrapper.last("LIMIT " + offset + "," + safePageSize);

        List<QuestionBankItemDto> items = questionBankItemMapper.selectList(listWrapper)
                .stream().map(this::toDto).toList();

        QuestionBankPageDto dto = new QuestionBankPageDto();
        dto.setTotal(total);
        dto.setPage(safePage);
        dto.setPageSize(safePageSize);
        dto.setItems(items);
        return dto;
    }

    public void delete(Long userId, Long itemId) {
        QuestionBankItem item = questionBankItemMapper.selectById(itemId);
        if (item == null) {
            throw new IllegalArgumentException("收藏条目不存在");
        }
        if (!item.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除该收藏");
        }
        questionBankItemMapper.deleteById(itemId);
    }

    private LambdaQueryWrapper<QuestionBankItem> buildListWrapper(Long userId,
                                                                  String tag,
                                                                  BigDecimal minScore,
                                                                  BigDecimal maxScore) {
        LambdaQueryWrapper<QuestionBankItem> wrapper = new LambdaQueryWrapper<QuestionBankItem>()
                .eq(QuestionBankItem::getUserId, userId);
        if (StringUtils.hasText(tag)) {
            wrapper.like(QuestionBankItem::getTag, tag.trim());
        }
        if (minScore != null) {
            wrapper.ge(QuestionBankItem::getScore, minScore);
        }
        if (maxScore != null) {
            wrapper.le(QuestionBankItem::getScore, maxScore);
        }
        return wrapper;
    }

    private void applySort(LambdaQueryWrapper<QuestionBankItem> wrapper, String sortBy, String sortOrder) {
        String field = StringUtils.hasText(sortBy) ? sortBy.trim().toLowerCase(Locale.ROOT) : "createdat";
        boolean asc = "asc".equalsIgnoreCase(sortOrder);

        if ("score".equals(field)) {
            if (asc) {
                wrapper.orderByAsc(QuestionBankItem::getScore).orderByDesc(QuestionBankItem::getCreatedAt);
            } else {
                wrapper.orderByDesc(QuestionBankItem::getScore).orderByDesc(QuestionBankItem::getCreatedAt);
            }
            return;
        }

        if (asc) {
            wrapper.orderByAsc(QuestionBankItem::getCreatedAt);
        } else {
            wrapper.orderByDesc(QuestionBankItem::getCreatedAt);
        }
    }

    private QuestionBankItemDto toDto(QuestionBankItem item) {
        QuestionBankItemDto dto = new QuestionBankItemDto();
        dto.setId(item.getId());
        dto.setQuestionId(item.getQuestionId());
        dto.setSessionId(item.getSessionId());
        dto.setScore(item.getScore());
        dto.setTag(item.getTag());
        dto.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().toString() : null);

        Map<String, Object> snapshot = item.getSourceSnapshotJson();
        if (snapshot != null) {
            dto.setQuestionStem(toStr(snapshot.get("questionStem")));
            dto.setDomainName(toStr(snapshot.get("domainName")));
            dto.setQuestionType(toStr(snapshot.get("questionType")));
            dto.setAnswerSummary(toStr(snapshot.get("answerSummary")));
            dto.setSourceCreatedAt(toStr(snapshot.get("sourceCreatedAt")));
        }
        return dto;
    }

    private BigDecimal readScore(InterviewAttempt attempt) {
        if (attempt == null || attempt.getEvaluationJson() == null) {
            return null;
        }
        Object score = attempt.getEvaluationJson().get("score");
        if (score instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return null;
    }

    private String summarizeAnswer(String answerText) {
        if (!StringUtils.hasText(answerText) || "[skip]".equals(answerText)) {
            return null;
        }
        String normalized = answerText.trim().replace("\r", " ").replace("\n", " ");
        if (normalized.length() <= 140) {
            return normalized;
        }
        return normalized.substring(0, 140);
    }

    private String resolveDomainName(InterviewSession session, InterviewQuestion question) {
        if (question != null && question.getGenerationContextJson() != null) {
            Object contextDomainName = question.getGenerationContextJson().get("domainName");
            if (contextDomainName instanceof String s && StringUtils.hasText(s)) {
                return s;
            }
        }
        if (session != null && session.getSyllabusJson() != null) {
            Object domainsObj = session.getSyllabusJson().get("domains");
            if (domainsObj instanceof List<?> domains) {
                String domainCode = resolveDomainCode(question);
                if (StringUtils.hasText(domainCode)) {
                    for (Object domain : domains) {
                        if (!(domain instanceof Map<?, ?> dm)) {
                            continue;
                        }
                        if (domainCode.equals(dm.get("domainCode"))) {
                            Object domainName = dm.get("domainName");
                            if (domainName instanceof String s && StringUtils.hasText(s)) {
                                return s;
                            }
                        }
                    }
                }
            }
        }
        String specialDomainName = InterviewDomainDisplaySupport.resolveSpecialDomainName(resolveDomainCode(question));
        if (StringUtils.hasText(specialDomainName)) {
            return specialDomainName;
        }
        String questionTypeLabel = InterviewDomainDisplaySupport.resolveQuestionTypeLabel(
                question == null ? null : question.getQuestionType());
        if (StringUtils.hasText(questionTypeLabel)) {
            return questionTypeLabel;
        }
        if (StringUtils.hasText(question.getFocusPoint())) {
            return question.getFocusPoint();
        }
        return "unknown";
    }

    private String resolveFocusPoint(InterviewQuestion question) {
        if (question != null && question.getGenerationContextJson() != null) {
            Object focusPoint = question.getGenerationContextJson().get("focusPoint");
            if (focusPoint instanceof String s && StringUtils.hasText(s)) {
                return s;
            }
        }
        if (question == null) {
            return null;
        }
        return question.getFocusPoint();
    }

    private String resolveDomainCode(InterviewQuestion question) {
        if (question == null || question.getGenerationContextJson() == null) {
            return question == null ? null : question.getDomainCode();
        }
        Object code = question.getGenerationContextJson().get("domainCode");
        if (code instanceof String s && StringUtils.hasText(s)) {
            return s;
        }
        return question.getDomainCode();
    }

    private String toStr(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }
}

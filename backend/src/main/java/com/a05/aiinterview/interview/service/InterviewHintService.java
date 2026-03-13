package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.InterviewHintResponse;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewHintService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;

    public InterviewHintResponse getHint(Long sessionId, Long questionId, Long userId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("面试会话不存在或无权访问");
        }

        InterviewQuestion question = interviewQuestionMapper.selectById(questionId);
        if (question == null || !sessionId.equals(question.getSessionId())) {
            throw new IllegalArgumentException("题目不存在或不属于该会话");
        }

        if (StringUtils.hasText(question.getHintText())) {
            return new InterviewHintResponse(question.getId(), question.getHintText(), "cached");
        }

        String hint = buildHint(question);
        InterviewQuestion update = new InterviewQuestion();
        update.setId(question.getId());
        update.setHintText(hint);
        update.setUpdatedAt(LocalDateTime.now());
        interviewQuestionMapper.updateById(update);

        return new InterviewHintResponse(question.getId(), hint, "generated");
    }

    private String buildHint(InterviewQuestion question) {
        List<String> expectedPoints = question.getExpectedPoints();
        if (expectedPoints != null && !expectedPoints.isEmpty()) {
            List<String> topPoints = expectedPoints.stream().filter(StringUtils::hasText).limit(3).toList();
            if (!topPoints.isEmpty()) {
                return "提示：回答时可优先覆盖这些要点：" + String.join("、", topPoints);
            }
        }

        if (StringUtils.hasText(question.getTargetSkill())) {
            return "提示：先定义「" + question.getTargetSkill() + "」，再说明方案取舍与落地结果。";
        }

        return "提示：可按“定义 -> 原理 -> 场景 -> 取舍”四步组织回答。";
    }
}

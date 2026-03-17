package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.mapper.SessionSkillStateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewManagementService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final SessionSkillStateMapper sessionSkillStateMapper;

    @Transactional(rollbackFor = Exception.class)
    public void deleteInterview(Long sessionId, Long userId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("面试会话不存在, sessionId=" + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除该面试会话");
        }

        interviewReportMapper.delete(new LambdaQueryWrapper<com.a05.aiinterview.interview.entity.InterviewReport>()
                .eq(com.a05.aiinterview.interview.entity.InterviewReport::getSessionId, sessionId));
        interviewAttemptMapper.delete(new LambdaQueryWrapper<com.a05.aiinterview.interview.entity.InterviewAttempt>()
                .eq(com.a05.aiinterview.interview.entity.InterviewAttempt::getSessionId, sessionId));
        interviewQuestionMapper.delete(new LambdaQueryWrapper<com.a05.aiinterview.interview.entity.InterviewQuestion>()
                .eq(com.a05.aiinterview.interview.entity.InterviewQuestion::getSessionId, sessionId));
        sessionSkillStateMapper.delete(new LambdaQueryWrapper<com.a05.aiinterview.interview.entity.SessionSkillState>()
                .eq(com.a05.aiinterview.interview.entity.SessionSkillState::getSessionId, sessionId));
        interviewSessionMapper.deleteById(sessionId);
    }
}

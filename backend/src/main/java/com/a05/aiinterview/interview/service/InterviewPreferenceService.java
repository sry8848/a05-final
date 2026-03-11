package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.entity.InterviewPreference;
import com.a05.aiinterview.interview.mapper.InterviewPreferenceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 面试偏好服务。
 * 每用户保留一条最新偏好记录，用于下次进入准备页时自动回填表单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewPreferenceService {

    private final InterviewPreferenceMapper preferenceMapper;

    /**
     * 获取当前用户最近一次面试偏好，若无记录则返回 empty。
     *
     * @param userId 当前登录用户 ID
     * @return 偏好实体 Optional
     */
    public Optional<InterviewPreference> getLatest(Long userId) {
        log.info("读取面试偏好, userId={}", userId);
        InterviewPreference pref = preferenceMapper.selectOne(
                new LambdaQueryWrapper<InterviewPreference>()
                        .eq(InterviewPreference::getUserId, userId)
        );
        return Optional.ofNullable(pref);
    }

    /**
     * 保存或更新当前用户的面试偏好（upsert：存在则更新，不存在则插入）。
     *
     * @param userId 当前登录用户 ID
     * @param pref   偏好数据（userId 字段可为空，方法内部会赋值）
     */
    public void saveOrUpdate(Long userId, InterviewPreference pref) {
        log.info("保存面试偏好, userId={}, targetRole={}, mode={}", userId, pref.getTargetRole(), pref.getMode());

        pref.setUserId(userId);
        pref.setUpdatedAt(LocalDateTime.now());

        // 查询是否已有记录
        InterviewPreference existing = preferenceMapper.selectOne(
                new LambdaQueryWrapper<InterviewPreference>()
                        .eq(InterviewPreference::getUserId, userId)
        );

        if (existing == null) {
            preferenceMapper.insert(pref);
            log.info("面试偏好新建完成, userId={}", userId);
        } else {
            pref.setId(existing.getId());
            preferenceMapper.update(pref, new LambdaUpdateWrapper<InterviewPreference>()
                    .eq(InterviewPreference::getUserId, userId));
            log.info("面试偏好更新完成, userId={}", userId);
        }
    }
}

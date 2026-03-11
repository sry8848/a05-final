package com.a05.aiinterview.position.service;

import com.a05.aiinterview.position.dto.PositionDto;
import com.a05.aiinterview.position.dto.SkillDomainDto;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import com.a05.aiinterview.position.mapper.PositionSkillDomainMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 岗位与知识域查询服务。
 * 提供岗位列表和指定岗位知识域树的查询能力，数据来源于 position_skill_domains 表的静态配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionSkillDomainMapper positionSkillDomainMapper;

    /**
     * 获取所有岗位列表（去重，每个 positionCode 仅返回一条）。
     *
     * @return 岗位列表 DTO
     */
    public List<PositionDto> listPositions() {
        log.info("查询岗位列表");

        // 查询最新版本（version 最大值）的所有记录，按 positionCode 去重
        List<PositionSkillDomain> all = positionSkillDomainMapper.selectList(
                new LambdaQueryWrapper<PositionSkillDomain>()
                        .orderByAsc(PositionSkillDomain::getPositionCode)
        );

        // 按 positionCode 去重，保留每个岗位第一条（positionCode + positionName 固定）
        List<PositionDto> result = all.stream()
                .collect(Collectors.toMap(
                        PositionSkillDomain::getPositionCode,
                        d -> new PositionDto(d.getPositionCode(), d.getPositionName()),
                        (existing, replacement) -> existing // 保留先出现的
                ))
                .values()
                .stream()
                .sorted((a, b) -> a.getPositionCode().compareTo(b.getPositionCode()))
                .collect(Collectors.toList());

        log.info("查询岗位列表完成，共 {} 个岗位", result.size());
        return result;
    }

    /**
     * 获取指定岗位下的知识域列表（取最新版本，按 sort_order 升序）。
     *
     * @param positionCode 岗位编码，如 JAVA_BACKEND
     * @return 知识域列表 DTO
     */
    public List<SkillDomainDto> listSkillDomains(String positionCode) {
        log.info("查询岗位知识域树, positionCode={}", positionCode);

        // 先查出该岗位最大版本号，确保始终返回最新版知识域
        List<PositionSkillDomain> domains = positionSkillDomainMapper.selectList(
                new LambdaQueryWrapper<PositionSkillDomain>()
                        .eq(PositionSkillDomain::getPositionCode, positionCode)
                        .orderByAsc(PositionSkillDomain::getSortOrder)
        );

        if (domains.isEmpty()) {
            log.info("岗位 {} 暂无知识域配置", positionCode);
            return List.of();
        }

        // 取最大版本号，过滤出最新版本的知识域
        int latestVersion = domains.stream()
                .mapToInt(PositionSkillDomain::getVersion)
                .max()
                .orElse(1);

        List<SkillDomainDto> result = domains.stream()
                .filter(d -> d.getVersion() == latestVersion)
                .map(d -> new SkillDomainDto(d.getId(), d.getDomainCode(), d.getDomainName(), d.getDescription()))
                .collect(Collectors.toList());

        log.info("查询岗位 {} 知识域完成，共 {} 个知识域（版本 v{}）", positionCode, result.size(), latestVersion);
        return result;
    }

    /**
     * 查询指定岗位最新版本的全部知识域实体（供内部服务调用，如 Planner 组装上下文）。
     *
     * @param positionCode 岗位编码
     * @return 知识域实体列表
     */
    public List<PositionSkillDomain> listSkillDomainEntities(String positionCode) {
        List<PositionSkillDomain> all = positionSkillDomainMapper.selectList(
                new LambdaQueryWrapper<PositionSkillDomain>()
                        .eq(PositionSkillDomain::getPositionCode, positionCode)
                        .orderByAsc(PositionSkillDomain::getSortOrder)
        );
        if (all.isEmpty()) {
            return List.of();
        }
        int latestVersion = all.stream().mapToInt(PositionSkillDomain::getVersion).max().orElse(1);
        return all.stream().filter(d -> d.getVersion() == latestVersion).collect(Collectors.toList());
    }
}

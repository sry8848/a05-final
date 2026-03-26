package com.a05.aiinterview.resume.service;

import com.a05.aiinterview.resume.config.ResumeStorageConfig;
import com.a05.aiinterview.resume.dto.*;
import com.a05.aiinterview.resume.entity.Resume;
import com.a05.aiinterview.resume.mapper.ResumeMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 简历管理服务：列表、上传、解析状态、详情、更新、设默认、删除。
 * 多租户：所有操作按 userId 隔离；每用户最多一份默认简历。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private static final String PARSE_STATUS_PARSING = "parsing";
    private static final String PARSE_STATUS_PARSED = "parsed";
    private static final String PARSE_STATUS_FAILED = "failed";
    private static final String SOURCE_TYPE_FILE = "file";
    private static final int PREVIEW_MAX_LEN = 500;
    private static final String EXT_PDF = ".pdf";
    private static final String EXT_DOCX = ".docx";
    private static final String EXT_MD = ".md";

    private final ResumeMapper resumeMapper;
    private final ResumeStorageConfig storageConfig;
    private final ResumeFileParser resumeFileParser;
    private TaskExecutor resumeParseTaskExecutor = new SimpleAsyncTaskExecutor("resume-parse-");

    @Autowired(required = false)
    void setResumeParseTaskExecutor(TaskExecutor resumeParseTaskExecutor) {
        if (resumeParseTaskExecutor != null) {
            this.resumeParseTaskExecutor = resumeParseTaskExecutor;
        }
    }

    /**
     * 获取当前用户的简历列表，按创建时间倒序。
     *
     * @param userId 当前用户ID
     * @return 列表项 DTO
     */
    public List<ResumeListItemDto> list(Long userId) {
        LambdaQueryWrapper<Resume> q = new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getCreatedAt);
        List<Resume> list = resumeMapper.selectList(q);
        log.info("简历列表查询完成, userId={}, count={}", userId, list.size());
        return list.stream().map(this::toListItemDto).collect(Collectors.toList());
    }

    /**
     * 上传简历：落库、存文件并解析。
     * PDF / DOCX 走异步解析；Markdown 直接读取原文并返回 parsed。
     *
     * @param userId 当前用户ID
     * @param file   上传文件（PDF/DOCX/MD）
     * @return 简历ID与解析状态
     */
    @Transactional(rollbackFor = Exception.class)
    public ResumeUploadResponseDto upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("").trim();
        if (originalName.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String lower = originalName.toLowerCase();
        String ext = resolveSupportedExtension(lower);
        if (ext == null) {
            throw new IllegalArgumentException("仅支持 PDF、DOCX、MD 格式");
        }
        boolean markdownUpload = EXT_MD.equals(ext);

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setName(originalName);
        resume.setSourceType(SOURCE_TYPE_FILE);
        resume.setParseStatus(PARSE_STATUS_PARSING);
        resume.setParsedText(null);
        resume.setIsDefault(false);
        resumeMapper.insert(resume);
        Long resumeId = resume.getId();

        Path baseDir = storageConfig.getUploadDirPath().resolve(String.valueOf(userId));
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            log.error("创建简历存储目录失败, userId={}, resumeId={}", userId, resumeId, e);
            throw new IllegalStateException("存储目录创建失败");
        }
        String relativePath = userId + "/" + resumeId + ext;
        Path targetFile = baseDir.resolve(resumeId + ext);
        try {
            file.transferTo(targetFile.toFile());
        } catch (IOException e) {
            log.error("简历文件保存失败, userId={}, resumeId={}", userId, resumeId, e);
            resumeMapper.deleteById(resumeId);
            throw new IllegalStateException("文件保存失败");
        }
        resume.setFilePath(relativePath);
        resumeMapper.updateById(resume);

        log.info("简历上传成功, userId={}, resumeId={}, name={}", userId, resumeId, originalName);
        if (markdownUpload) {
            try {
                String text = resumeFileParser.parse(targetFile);
                resume.setParsedText(text);
                resume.setParseStatus(PARSE_STATUS_PARSED);
                resumeMapper.updateById(resume);
                log.info("Markdown 简历解析完成, resumeId={}, length={}", resumeId, text != null ? text.length() : 0);
                return new ResumeUploadResponseDto(resumeId, PARSE_STATUS_PARSED);
            } catch (Exception e) {
                log.error("Markdown 简历解析失败, resumeId={}, path={}", resumeId, targetFile, e);
                resume.setParseStatus(PARSE_STATUS_FAILED);
                resumeMapper.updateById(resume);
                return new ResumeUploadResponseDto(resumeId, PARSE_STATUS_FAILED);
            }
        }

        resumeParseTaskExecutor.execute(() -> runAsyncParse(resumeId));
        return new ResumeUploadResponseDto(resumeId, PARSE_STATUS_PARSING);
    }

    /**
     * 解析简历文件并更新 parsedText / parseStatus。
     * 由 TaskExecutor 异步调度，避免类内 self-invocation 导致 @Async 失效。
     */
    public void runAsyncParse(Long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null || !PARSE_STATUS_PARSING.equals(resume.getParseStatus())) {
            return;
        }
        Path fullPath = storageConfig.getUploadDirPath().resolve(resume.getFilePath());
        try {
            String text = resumeFileParser.parse(fullPath);
            resume.setParsedText(text);
            resume.setParseStatus(PARSE_STATUS_PARSED);
            resumeMapper.updateById(resume);
            log.info("简历解析完成, resumeId={}, length={}", resumeId, text != null ? text.length() : 0);
        } catch (Exception e) {
            log.error("简历解析失败, resumeId={}, path={}", resumeId, fullPath, e);
            resume.setParseStatus(PARSE_STATUS_FAILED);
            resumeMapper.updateById(resume);
        }
    }

    /**
     * 查询解析状态（含预览）。
     *
     * @param userId   当前用户ID
     * @param resumeId 简历ID
     * @return 解析状态 DTO
     */
    public ResumeParseStatusDto getParseStatus(Long userId, Long resumeId) {
        Resume resume = getOwnedResume(userId, resumeId);
        String preview = null;
        if (PARSE_STATUS_PARSED.equals(resume.getParseStatus()) && resume.getParsedText() != null) {
            String t = resume.getParsedText().trim();
            preview = t.length() <= PREVIEW_MAX_LEN ? t : t.substring(0, PREVIEW_MAX_LEN) + "...";
        }
        return new ResumeParseStatusDto(resume.getId(), resume.getParseStatus(), preview);
    }

    /**
     * 获取简历详情（含完整识别文本）。
     */
    public ResumeDetailDto getDetail(Long userId, Long resumeId) {
        Resume resume = getOwnedResume(userId, resumeId);
        return toDetailDto(resume);
    }

    /**
     * 更新简历名称、识别文本、是否默认。
     * 若 isDefault=true，先清除该用户其它简历的默认标记。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, Long resumeId, ResumeUpdateRequestDto dto) {
        Resume resume = getOwnedResume(userId, resumeId);
        if (dto.getName() != null) {
            resume.setName(dto.getName().trim());
        }
        if (dto.getParsedText() != null) {
            resume.setParsedText(dto.getParsedText());
        }
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearDefaultForUser(userId);
            resume.setIsDefault(true);
        } else if (dto.getIsDefault() != null && !dto.getIsDefault()) {
            resume.setIsDefault(false);
        }
        resumeMapper.updateById(resume);
        log.info("简历更新成功, userId={}, resumeId={}", userId, resumeId);
    }

    /**
     * 设为默认简历；该用户其它简历的默认标记会被清除。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long resumeId) {
        getOwnedResume(userId, resumeId);
        clearDefaultForUser(userId);
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId)
                .set(Resume::getIsDefault, true));
        log.info("已设为默认简历, userId={}, resumeId={}", userId, resumeId);
    }

    /**
     * 删除简历（物理删除记录与本地文件）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long resumeId) {
        Resume resume = getOwnedResume(userId, resumeId);
        if (resume.getFilePath() != null && !resume.getFilePath().isEmpty()) {
            Path path = storageConfig.getUploadDirPath().resolve(resume.getFilePath());
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("删除简历文件失败, resumeId={}, path={}", resumeId, path, e);
            }
        }
        resumeMapper.deleteById(resumeId);
        log.info("简历已删除, userId={}, resumeId={}", userId, resumeId);
    }

    private String resolveSupportedExtension(String lowerName) {
        if (lowerName.endsWith(EXT_PDF)) {
            return EXT_PDF;
        }
        if (lowerName.endsWith(EXT_DOCX)) {
            return EXT_DOCX;
        }
        if (lowerName.endsWith(EXT_MD)) {
            return EXT_MD;
        }
        return null;
    }

    private Resume getOwnedResume(Long userId, Long resumeId) {
        Resume r = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId));
        if (r == null) {
            throw new IllegalArgumentException("简历不存在或无权访问");
        }
        return r;
    }

    private void clearDefaultForUser(Long userId) {
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .set(Resume::getIsDefault, false));
    }

    private ResumeListItemDto toListItemDto(Resume r) {
        ResumeListItemDto dto = new ResumeListItemDto();
        dto.setId(r.getId());
        dto.setName(r.getName());
        dto.setSourceType(r.getSourceType());
        dto.setParseStatus(r.getParseStatus());
        dto.setIsDefault(Boolean.TRUE.equals(r.getIsDefault()));
        dto.setCreatedAt(r.getCreatedAt() == null ? null : r.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        return dto;
    }

    private ResumeDetailDto toDetailDto(Resume r) {
        ResumeDetailDto dto = new ResumeDetailDto();
        dto.setId(r.getId());
        dto.setName(r.getName());
        dto.setSourceType(r.getSourceType());
        dto.setParseStatus(r.getParseStatus());
        dto.setParsedText(r.getParsedText());
        dto.setIsDefault(Boolean.TRUE.equals(r.getIsDefault()));
        dto.setCreatedAt(r.getCreatedAt() == null ? null : r.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        return dto;
    }
}

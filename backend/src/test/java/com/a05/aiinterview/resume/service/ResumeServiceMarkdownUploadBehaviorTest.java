package com.a05.aiinterview.resume.service;

import com.a05.aiinterview.resume.config.ResumeStorageConfig;
import com.a05.aiinterview.resume.dto.ResumeUploadResponseDto;
import com.a05.aiinterview.resume.entity.Resume;
import com.a05.aiinterview.resume.mapper.ResumeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ResumeService markdown upload behavior tests")
class ResumeServiceMarkdownUploadBehaviorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("upload should parse md immediately and skip async executor")
    void uploadShouldParseMdImmediatelyAndSkipAsyncExecutor() throws Exception {
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        ResumeStorageConfig storageConfig = new ResumeStorageConfig();
        storageConfig.setUploadDir(tempDir.toString());
        ResumeFileParser resumeFileParser = mock(ResumeFileParser.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);

        ResumeService service = new ResumeService(resumeMapper, storageConfig, resumeFileParser);
        service.setResumeParseTaskExecutor(taskExecutor);

        List<Resume> updatedResumes = new ArrayList<>();
        doAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(77L);
            return 1;
        }).when(resumeMapper).insert(any(Resume.class));
        doAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            Resume snapshot = new Resume();
            snapshot.setId(resume.getId());
            snapshot.setName(resume.getName());
            snapshot.setFilePath(resume.getFilePath());
            snapshot.setParseStatus(resume.getParseStatus());
            snapshot.setParsedText(resume.getParsedText());
            updatedResumes.add(snapshot);
            return 1;
        }).when(resumeMapper).updateById(any(Resume.class));

        String markdown = "# AI 面试\n\n- Vue\n- Spring Boot";
        when(resumeFileParser.parse(any(Path.class))).thenReturn(markdown);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "candidate.md",
                "text/markdown",
                markdown.getBytes(StandardCharsets.UTF_8)
        );

        ResumeUploadResponseDto response = service.upload(7L, file);

        assertThat(response.getResumeId()).isEqualTo(77L);
        assertThat(response.getParseStatus()).isEqualTo("parsed");
        assertThat(updatedResumes).hasSize(2);
        assertThat(updatedResumes.get(0).getFilePath()).isEqualTo("7/77.md");
        assertThat(updatedResumes.get(1).getParseStatus()).isEqualTo("parsed");
        assertThat(updatedResumes.get(1).getParsedText()).isEqualTo(markdown);
        verify(resumeFileParser).parse(eq(tempDir.resolve("7").resolve("77.md")));
        verifyNoInteractions(taskExecutor);
    }
}

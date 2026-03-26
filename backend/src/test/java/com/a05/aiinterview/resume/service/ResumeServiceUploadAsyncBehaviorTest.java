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
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@DisplayName("ResumeService upload async behavior tests")
class ResumeServiceUploadAsyncBehaviorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("upload should return without waiting for parse execution")
    void uploadShouldReturnWithoutWaitingForParseExecution() throws Exception {
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        ResumeStorageConfig storageConfig = new ResumeStorageConfig();
        storageConfig.setUploadDir(tempDir.toString());

        ResumeService service = spy(new ResumeService(
                resumeMapper,
                storageConfig,
                mock(ResumeFileParser.class)
        ));
        service.setResumeParseTaskExecutor((TaskExecutor) command -> {
            Thread worker = new Thread(command, "resume-upload-test-worker");
            worker.setDaemon(true);
            worker.start();
        });

        doAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(42L);
            return 1;
        }).when(resumeMapper).insert(any(Resume.class));
        when(resumeMapper.updateById(any(Resume.class))).thenReturn(1);

        doAnswer(invocation -> {
            Thread.sleep(400);
            return null;
        }).when(service).runAsyncParse(anyLong());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "candidate.pdf",
                "application/pdf",
                "fake-pdf".getBytes(StandardCharsets.UTF_8)
        );

        long startedAt = System.nanoTime();
        ResumeUploadResponseDto response = service.upload(7L, file);
        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertThat(response.getResumeId()).isEqualTo(42L);
        assertThat(response.getParseStatus()).isEqualTo("parsing");
        assertThat(durationMs).isLessThan(250L);
    }
}

package com.a05.aiinterview.resume.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResumeFileParser markdown support tests")
class ResumeFileParserMarkdownTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("parse should keep original markdown text for md files")
    void parseShouldKeepOriginalMarkdownTextForMdFiles() throws Exception {
        ResumeFileParser parser = new ResumeFileParser();
        Path markdownFile = tempDir.resolve("candidate.md");
        String markdown = """
                # Java 后端工程师

                - Spring Boot
                - MyBatis-Plus

                ```java
                System.out.println("hello");
                ```
                """;

        Files.writeString(markdownFile, markdown, StandardCharsets.UTF_8);

        assertThat(parser.parse(markdownFile)).isEqualTo(markdown.trim());
    }
}

package com.a05.aiinterview.resume.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 简历上传与存储配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "resume")
public class ResumeStorageConfig {

    /**
     * 存储根目录（可为相对或绝对路径）。
     */
    private String uploadDir = System.getProperty("java.io.tmpdir") + "/aiinterview-resumes";

    /**
     * 解析后的存储根目录 Path，若目录不存在会尝试创建。
     */
    public Path getUploadDirPath() {
        Path p = Paths.get(uploadDir).toAbsolutePath();
        try {
            if (!p.toFile().exists()) {
                p.toFile().mkdirs();
            }
        } catch (Exception e) {
            // 创建失败时仍返回 path，后续写入时再报错
        }
        return p;
    }
}

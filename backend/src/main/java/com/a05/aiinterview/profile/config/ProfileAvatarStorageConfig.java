package com.a05.aiinterview.profile.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Data
@Configuration
@ConfigurationProperties(prefix = "profile.avatar")
public class ProfileAvatarStorageConfig {

    private String uploadDir = System.getProperty("java.io.tmpdir") + "/aiinterview-avatars";

    public Path getUploadDirPath() {
        Path path = Paths.get(uploadDir).toAbsolutePath();
        try {
            if (!path.toFile().exists()) {
                path.toFile().mkdirs();
            }
        } catch (Exception ignored) {
        }
        return path;
    }
}

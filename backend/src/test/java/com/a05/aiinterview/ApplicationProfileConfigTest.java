package com.a05.aiinterview;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ApplicationProfileConfigTest {

    @Test
    void sharedConfigShouldNotHardcodeActiveProfile() throws IOException {
        String yaml = new ClassPathResource("application.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertFalse(
                yaml.contains("active: local"),
                "application.yml should not hardcode spring.profiles.active; IDEA should select local explicitly"
        );
    }
}

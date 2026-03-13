package com.a05.aiinterview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
/** 仅扫描各模块的 mapper 包 */
@MapperScan(basePackages = {
        "com.a05.aiinterview.auth.mapper",
        "com.a05.aiinterview.resume.mapper",
        "com.a05.aiinterview.position.mapper",
        "com.a05.aiinterview.interview.mapper",
        "com.a05.aiinterview.ai.mapper",
        "com.a05.aiinterview.questionbank.mapper"
})
public class AiInterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiInterviewApplication.class, args);
    }
}

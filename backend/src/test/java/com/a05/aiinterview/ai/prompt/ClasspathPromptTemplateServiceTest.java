package com.a05.aiinterview.ai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Classpath Prompt 模板渲染测试")
class ClasspathPromptTemplateServiceTest {

    @Test
    @DisplayName("渲染 planner 模板成功：替换 user 模板变量并返回 system/user 文本")
    void renderPlanner_shouldReplaceUserVariables() {
        PromptTemplateService service = new ClasspathPromptTemplateService(new ObjectMapper());

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("position", "Java 后端开发");
        vars.put("positionCode", "JAVA_BACKEND");
        vars.put("experienceLevel", "SENIOR");
        vars.put("mode", "professional");
        vars.put("jd", "负责高并发交易系统开发");
        vars.put("resumeText", "熟悉 JVM、并发、MySQL");
        vars.put("focusTopics", "并发,JVM");
        vars.put("domains", "- id=1, code=jvm, name=JVM 原理");

        RenderedPrompt rendered = service.render("planner", "v2", vars);

        assertThat(rendered).isNotNull();
        assertThat(rendered.getPromptCode()).isEqualTo("planner");
        assertThat(rendered.getPromptVersion()).isEqualTo("v2");
        assertThat(rendered.getSystemPrompt()).contains("你是一名经验丰富的技术面试官");
        assertThat(rendered.getUserPrompt()).contains("岗位：Java 后端开发（JAVA_BACKEND）");
        assertThat(rendered.getUserPrompt()).doesNotContain("{{position}}");
    }

    @Test
    @DisplayName("缺失变量时渲染失败：抛出缺参异常并包含变量名")
    void renderPlanner_missingVar_shouldThrow() {
        PromptTemplateService service = new ClasspathPromptTemplateService(new ObjectMapper());

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("position", "Java 后端开发");
        vars.put("positionCode", "JAVA_BACKEND");
        vars.put("experienceLevel", "SENIOR");
        vars.put("mode", "professional");
        vars.put("jd", "负责高并发交易系统开发");
        vars.put("resumeText", "熟悉 JVM、并发、MySQL");
        vars.put("focusTopics", "并发,JVM");

        assertThatThrownBy(() -> service.render("planner", "v2", vars))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("domains");
    }

    @Test
    @DisplayName("模板版本不匹配时渲染失败")
    void renderVersionMismatch_shouldThrow() {
        PromptTemplateService service = new ClasspathPromptTemplateService(new ObjectMapper());
        Map<String, Object> vars = Map.of(
                "position", "Java 后端开发",
                "positionCode", "JAVA_BACKEND",
                "experienceLevel", "SENIOR",
                "mode", "professional",
                "jd", "",
                "resumeText", "",
                "focusTopics", "",
                "domains", ""
        );

        assertThatThrownBy(() -> service.render("planner", "v999", vars))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("planner")
                .hasMessageContaining("v999")
                .hasMessageContaining("v2");
    }

    @Test
    @DisplayName("loadMetadata should expose prompt code, version and source path")
    void loadMetadata_shouldExposePromptMeta() {
        PromptTemplateService service = new ClasspathPromptTemplateService(new ObjectMapper());

        PromptTemplateMetadata metadata = service.loadMetadata("intro_rewrite");

        assertThat(metadata.promptCode()).isEqualTo("intro_rewrite");
        assertThat(metadata.promptVersion()).isEqualTo("v1");
        assertThat(metadata.sourcePath()).contains("prompts/intro-rewrite.md");
    }
}

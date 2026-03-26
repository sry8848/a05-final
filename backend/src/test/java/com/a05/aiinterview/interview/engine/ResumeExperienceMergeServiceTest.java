package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.PlannerOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResumeExperienceMergeService tests")
class ResumeExperienceMergeServiceTest {

    private final ResumeExperienceMergeService service = new ResumeExperienceMergeService();

    @Test
    @DisplayName("merge should keep planner project order and append missing resume projects")
    void merge_shouldKeepPlannerProjectOrderAndAppendMissingResumeProjects() {
        String resumeText = """
                项目经历
                AI 模拟面试系统 | Java 后端开发 | 2026.03 - 至今
                项目描述：利用大语言模型提供沉浸式模拟面试。
                对话上下文管理：使用 Redis 管理多轮对话上下文。

                苍穹外卖（企业级餐饮外卖平台） | Java 后端开发 | 2025.12 - 2026.03
                项目描述：提供完整 O2O 餐饮业务闭环。
                订单业务处理：使用 Spring 事务保证订单一致性。
                """;

        PlannerOutput.ExperienceItem plannerItem = PlannerOutput.ExperienceItem.builder()
                .itemType("PROJECT")
                .itemName("苍穹外卖（企业级餐饮外卖平台）")
                .resumeDescription("提供完整 O2O 餐饮业务闭环。")
                .techHooks(List.of("Redis 缓存菜品分类及详情数据"))
                .build();

        List<PlannerOutput.ExperienceItem> merged = service.merge(resumeText, List.of(plannerItem));

        assertThat(merged)
                .extracting(PlannerOutput.ExperienceItem::getItemName)
                .containsExactly(
                        "苍穹外卖（企业级餐饮外卖平台）",
                        "AI 模拟面试系统"
                );
    }

    @Test
    @DisplayName("merge should preserve planner hooks for matched project and derive hooks for missing project")
    void merge_shouldPreservePlannerHooksAndDeriveHooksForMissingProject() {
        String resumeText = """
                项目经历
                AI 模拟面试系统 | Java 后端开发 | 2026.03 - 至今
                项目描述：利用大语言模型提供沉浸式模拟面试。
                核心模块开发：基于 Spring Boot 构建后端服务。
                对话上下文管理：使用 Redis 管理多轮对话上下文。
                """;

        PlannerOutput.ExperienceItem plannerItem = PlannerOutput.ExperienceItem.builder()
                .itemType("PROJECT")
                .itemName("AI 模拟面试系统")
                .resumeDescription("利用大语言模型提供沉浸式模拟面试。")
                .techHooks(List.of("planner 优先 hook"))
                .build();

        List<PlannerOutput.ExperienceItem> mergedWithMatch = service.merge(resumeText, List.of(plannerItem));

        assertThat(mergedWithMatch).hasSize(1);
        assertThat(mergedWithMatch.getFirst().getTechHooks()).containsExactly("planner 优先 hook");

        List<PlannerOutput.ExperienceItem> mergedWithoutMatch = service.merge(resumeText, List.of());

        assertThat(mergedWithoutMatch).hasSize(1);
        assertThat(mergedWithoutMatch.getFirst().getTechHooks())
                .contains("核心模块开发", "对话上下文管理")
                .doesNotContain("项目描述");
    }
}

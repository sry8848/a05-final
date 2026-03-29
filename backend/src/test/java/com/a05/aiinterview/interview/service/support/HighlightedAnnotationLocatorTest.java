package com.a05.aiinterview.interview.service.support;

import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationOutput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighlightedAnnotationLocatorTest {

    @Test
    void locate_shouldCreateCoordinatesForUniqueQuote() {
        List<QuestionDetailEvaluationOutput.HighlightedAnnotation> annotations =
                HighlightedAnnotationLocator.locate(
                        "我们遵循的核心原则是尽量消除共享，必须共享时确保原子性。",
                        List.of(QuestionDetailEvaluationOutput.HighlightedAnnotation.builder()
                                .quote("尽量消除共享")
                                .label("strength")
                                .comment("抓住原则")
                                .build())
                );

        assertEquals(1, annotations.size());
        assertEquals("我们遵循的核心原则是尽量消除共享，必须共享时确保原子性。".indexOf("尽量消除共享"), annotations.getFirst().getStart());
        assertEquals("我们遵循的核心原则是尽量消除共享，必须共享时确保原子性。".indexOf("尽量消除共享") + "尽量消除共享".length(), annotations.getFirst().getEnd());
        assertEquals("尽量消除共享", annotations.getFirst().getQuote());
    }

    @Test
    void locate_shouldDropDuplicateSingleWordMatches() {
        List<QuestionDetailEvaluationOutput.HighlightedAnnotation> annotations =
                HighlightedAnnotationLocator.locate(
                        "CAS 保证原子性，CAS 失败时再重试。",
                        List.of(QuestionDetailEvaluationOutput.HighlightedAnnotation.builder()
                                .quote("CAS")
                                .label("strength")
                                .comment("重复词")
                                .build())
                );

        assertTrue(annotations.isEmpty());
    }

    @Test
    void locate_shouldSupportNormalizedQuotes() {
        List<QuestionDetailEvaluationOutput.HighlightedAnnotation> annotations =
                HighlightedAnnotationLocator.locate(
                        "我会先说明“缓存击穿”，再补充解决方案。",
                        List.of(QuestionDetailEvaluationOutput.HighlightedAnnotation.builder()
                                .quote("先说明\"缓存击穿\"")
                                .label("strength")
                                .comment("引号归一化")
                                .build())
                );

        assertEquals(1, annotations.size());
        assertEquals("先说明\"缓存击穿\"", annotations.getFirst().getQuote());
        assertEquals("我会先说明“缓存击穿”，再补充解决方案。".indexOf("先说明“缓存击穿”"), annotations.getFirst().getStart());
    }

    @Test
    void locate_shouldDropOverlyLongQuotes() {
        List<QuestionDetailEvaluationOutput.HighlightedAnnotation> annotations =
                HighlightedAnnotationLocator.locate(
                        "这是一个很长的回答内容，用来模拟模型不应该返回整段文本的场景，而且这里继续补充更多无关上下文，让整条引用明显超过限制。",
                        List.of(QuestionDetailEvaluationOutput.HighlightedAnnotation.builder()
                                .quote("这是一个很长的回答内容，用来模拟模型不应该返回整段文本的场景，而且这里继续补充更多无关上下文，让整条引用明显超过限制。")
                                .label("weakness")
                                .comment("整段")
                                .build())
                );

        assertTrue(annotations.isEmpty());
    }
}

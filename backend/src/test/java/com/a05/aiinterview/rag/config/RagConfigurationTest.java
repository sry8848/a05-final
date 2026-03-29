package com.a05.aiinterview.rag.config;

import com.google.common.util.concurrent.Futures;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RagConfiguration tests")
class RagConfigurationTest {

    @Test
    @DisplayName("ensureLexicalPayloadIndexes should register text and keyword indexes for lexical prefilter fields")
    void ensureLexicalPayloadIndexes_shouldRegisterTextAndKeywordIndexesForLexicalPrefilterFields() throws Exception {
        RagConfiguration configuration = new RagConfiguration();
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagProperties properties = new RagProperties();
        properties.setCollectionName("interview_knowledge");

        when(qdrantClient.createPayloadIndexAsync(
                eq("interview_knowledge"),
                any(String.class),
                any(Collections.PayloadSchemaType.class),
                any(Collections.PayloadIndexParams.class),
                eq(Boolean.TRUE),
                eq(Points.WriteOrderingType.Weak),
                any(Duration.class)
        )).thenReturn(Futures.immediateFuture(Points.UpdateResult.newBuilder().build()));

        configuration.ensureLexicalPayloadIndexes(qdrantClient, properties);

        verify(qdrantClient, times(1)).createPayloadIndexAsync(
                eq("interview_knowledge"),
                eq("question_text"),
                eq(Collections.PayloadSchemaType.Text),
                any(Collections.PayloadIndexParams.class),
                eq(Boolean.TRUE),
                eq(Points.WriteOrderingType.Weak),
                any(Duration.class)
        );
        verify(qdrantClient, times(1)).createPayloadIndexAsync(
                eq("interview_knowledge"),
                eq("intent_concept"),
                eq(Collections.PayloadSchemaType.Text),
                any(Collections.PayloadIndexParams.class),
                eq(Boolean.TRUE),
                eq(Points.WriteOrderingType.Weak),
                any(Duration.class)
        );
        verify(qdrantClient, times(1)).createPayloadIndexAsync(
                eq("interview_knowledge"),
                eq("keywords"),
                eq(Collections.PayloadSchemaType.Keyword),
                any(Collections.PayloadIndexParams.class),
                eq(Boolean.TRUE),
                eq(Points.WriteOrderingType.Weak),
                any(Duration.class)
        );
    }

    @Test
    @DisplayName("text index params should use multilingual lowercase tokenization")
    void lexicalTextIndexParams_shouldUseMultilingualLowercaseTokenization() {
        RagConfiguration configuration = new RagConfiguration();

        Collections.PayloadIndexParams params = configuration.lexicalTextIndexParams();

        assertThat(params.hasTextIndexParams()).isTrue();
        assertThat(params.getTextIndexParams().getTokenizer()).isEqualTo(Collections.TokenizerType.Multilingual);
        assertThat(params.getTextIndexParams().getLowercase()).isTrue();
    }
}

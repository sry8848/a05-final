package com.a05.aiinterview.rag.config;

import com.a05.aiinterview.rag.qdrant.QdrantHybridCollectionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("RagConfiguration tests")
class RagConfigurationTest {

    @Test
    @DisplayName("ragHybridCollectionManager should expose native hybrid schema manager")
    void ragHybridCollectionManager_shouldExposeNativeHybridSchemaManager() {
        RagConfiguration configuration = new RagConfiguration();
        RagProperties properties = new RagProperties();

        QdrantHybridCollectionManager manager = configuration.ragHybridCollectionManager(
                mock(io.qdrant.client.QdrantClient.class),
                properties
        );

        org.assertj.core.api.Assertions.assertThat(manager).isNotNull();
    }

    @Test
    @DisplayName("ragHybridSchemaInitializer should invoke manager when initializeSchema is enabled")
    void ragHybridSchemaInitializer_shouldInvokeManagerWhenInitializeSchemaEnabled() throws Exception {
        RagConfiguration configuration = new RagConfiguration();
        RagProperties properties = new RagProperties();
        properties.setInitializeSchema(true);
        QdrantHybridCollectionManager manager = mock(QdrantHybridCollectionManager.class);

        SmartInitializingSingleton initializer = configuration.ragHybridSchemaInitializer(manager, properties);
        initializer.afterSingletonsInstantiated();

        verify(manager).ensureCollectionSchema();
    }

    @Test
    @DisplayName("ragHybridSchemaInitializer should skip manager when initializeSchema is disabled")
    void ragHybridSchemaInitializer_shouldSkipManagerWhenInitializeSchemaDisabled() {
        RagConfiguration configuration = new RagConfiguration();
        RagProperties properties = new RagProperties();
        properties.setInitializeSchema(false);
        QdrantHybridCollectionManager manager = mock(QdrantHybridCollectionManager.class);

        SmartInitializingSingleton initializer = configuration.ragHybridSchemaInitializer(manager, properties);
        initializer.afterSingletonsInstantiated();

        verifyNoInteractions(manager);
    }
}

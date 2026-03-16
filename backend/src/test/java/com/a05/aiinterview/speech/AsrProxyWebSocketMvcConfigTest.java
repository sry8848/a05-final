package com.a05.aiinterview.speech;

import com.a05.aiinterview.speech.config.SpeechProperties;
import com.a05.aiinterview.speech.service.AliyunAsrWebSocketClient;
import com.a05.aiinterview.speech.service.AsrConstrainedCorrectionService;
import com.a05.aiinterview.speech.service.AsrProxyTicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.websocket.server.ServerContainer;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsrProxyWebSocketMvcConfigTest {

    @Test
    void onStartup_shouldRegisterListenerAndAddEndpointOnContextInitialized() throws Exception {
        AsrProxyWebSocketMvcConfig config = new AsrProxyWebSocketMvcConfig(
                mock(AsrProxyTicketService.class),
                mock(AliyunAsrWebSocketClient.class),
                new AsrConstrainedCorrectionService(),
                new ObjectMapper(),
                new SpeechProperties()
        );

        ServletContext servletContext = mock(ServletContext.class);
        ServerContainer serverContainer = mock(ServerContainer.class);
        org.mockito.ArgumentCaptor<ServletContextListener> listenerCaptor =
                org.mockito.ArgumentCaptor.forClass(ServletContextListener.class);

        when(servletContext.getAttribute("jakarta.websocket.server.ServerContainer"))
                .thenReturn(serverContainer);
        doNothing().when(serverContainer).addEndpoint(any(jakarta.websocket.server.ServerEndpointConfig.class));

        config.onStartup(servletContext);
        verify(servletContext).addListener(listenerCaptor.capture());

        listenerCaptor.getValue().contextInitialized(new ServletContextEvent(servletContext));

        verify(serverContainer).addEndpoint(any(jakarta.websocket.server.ServerEndpointConfig.class));
    }
}

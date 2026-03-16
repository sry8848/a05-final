package com.a05.aiinterview.speech;

import com.a05.aiinterview.speech.config.SpeechProperties;
import com.a05.aiinterview.speech.service.AliyunAsrWebSocketClient;
import com.a05.aiinterview.speech.service.AsrConstrainedCorrectionService;
import com.a05.aiinterview.speech.service.AsrProxyTicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsrProxyWebSocketEndpointTest {

    @Test
    void onOpen_shouldRejectInvalidTicket() throws Exception {
        AsrProxyTicketService ticketService = mock(AsrProxyTicketService.class);
        AliyunAsrWebSocketClient aliyunClient = mock(AliyunAsrWebSocketClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SpeechProperties speechProperties = new SpeechProperties();

        Session session = mock(Session.class);
        RemoteEndpoint.Basic basicRemote = mock(RemoteEndpoint.Basic.class);
        when(session.getRequestParameterMap()).thenReturn(Map.of("ticket", List.of("bad-ticket")));
        when(session.getBasicRemote()).thenReturn(basicRemote);
        when(ticketService.claimTicket("bad-ticket")).thenReturn(null);

        AsrProxyWebSocketEndpoint endpoint = new AsrProxyWebSocketEndpoint(
                ticketService,
                aliyunClient,
                new AsrConstrainedCorrectionService(),
                objectMapper,
                speechProperties);

        endpoint.onOpen(session, mock(EndpointConfig.class));

        verify(basicRemote).sendText(any());
        verify(session).close(any(CloseReason.class));
    }

    @Test
    void onOpen_shouldSendStructuredErrorPayloadWhenTicketInvalid() throws Exception {
        AsrProxyTicketService ticketService = mock(AsrProxyTicketService.class);
        AliyunAsrWebSocketClient aliyunClient = mock(AliyunAsrWebSocketClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SpeechProperties speechProperties = new SpeechProperties();

        Session session = mock(Session.class);
        RemoteEndpoint.Basic basicRemote = mock(RemoteEndpoint.Basic.class);
        when(session.getRequestParameterMap()).thenReturn(Map.of("ticket", List.of("expired-ticket")));
        when(session.getBasicRemote()).thenReturn(basicRemote);
        when(ticketService.claimTicket("expired-ticket")).thenReturn(null);

        AsrProxyWebSocketEndpoint endpoint = new AsrProxyWebSocketEndpoint(
                ticketService,
                aliyunClient,
                new AsrConstrainedCorrectionService(),
                objectMapper,
                speechProperties);

        endpoint.onOpen(session, mock(EndpointConfig.class));

        org.mockito.ArgumentCaptor<String> payloadCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(basicRemote).sendText(payloadCaptor.capture());
        com.fasterxml.jackson.databind.JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertEquals("error", payload.path("type").asText());
        assertEquals("proxy", payload.path("source").asText());
        assertEquals("invalid_ticket", payload.path("code").asText());
        assertEquals("ASR ticket 无效、已过期或已被使用", payload.path("message").asText());
        assertEquals("", payload.path("taskId").asText());
        assertEquals(false, payload.path("retryable").asBoolean());
        verify(session).close(any(CloseReason.class));
    }
}

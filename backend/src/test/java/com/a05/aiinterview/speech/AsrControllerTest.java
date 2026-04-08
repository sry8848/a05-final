package com.a05.aiinterview.speech;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.speech.config.SpeechProperties;
import com.a05.aiinterview.speech.dto.AsrTokenResponse;
import com.a05.aiinterview.speech.service.AsrProxyTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsrControllerTest {

    @Test
    void getAsrToken_shouldReturnProxyWsPathAndAudioConfig() {
        SpeechProperties properties = new SpeechProperties();
        properties.getAsr().setEnabled(true);
        properties.getAsr().setTokenTtlSeconds(300);

        AsrProxyTicketService ticketService = mock(AsrProxyTicketService.class);
        when(ticketService.issueTicket(42L)).thenReturn(
                AsrProxyTicketService.AsrProxyTicket.builder()
                        .ticket("ticket-123")
                        .expiresAt(1741800000L)
                        .build());

        AsrController controller = new AsrController(properties, ticketService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/asr/token");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setContextPath("/api/v1");

        ApiResponse<AsrTokenResponse> response = controller.getAsrToken(42L, request);

        AsrTokenResponse data = response.getData();
        assertNotNull(data);
        assertTrue(data.isEnabled());
        assertEquals("/api/v1/asr/stream?ticket=ticket-123", data.getWsUrl());
        assertEquals("ticket-123", data.getTicket());
        assertEquals(1741800000L, data.getExpiresAt());
        assertEquals("v1", data.getProtocolVersion());
        assertEquals("pcm", data.getAudio().getFormat());
        assertEquals(16000, data.getAudio().getSampleRate());
        assertEquals(1, data.getAudio().getChannels());
        assertEquals("pcm16le", data.getAudio().getEncoding());
    }

    @Test
    void getAsrToken_shouldReturnDisabledWhenAsrClosed() {
        SpeechProperties properties = new SpeechProperties();
        properties.getAsr().setEnabled(false);

        AsrController controller = new AsrController(properties, mock(AsrProxyTicketService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/asr/token");

        ApiResponse<AsrTokenResponse> response = controller.getAsrToken(42L, request);

        assertFalse(response.getData().isEnabled());
    }
}

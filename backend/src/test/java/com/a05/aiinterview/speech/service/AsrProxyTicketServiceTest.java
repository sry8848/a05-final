package com.a05.aiinterview.speech.service;

import com.a05.aiinterview.speech.config.SpeechProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsrProxyTicketServiceTest {

    @Test
    void issueTicket_shouldPersistOwnerWithConfiguredTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SpeechProperties properties = new SpeechProperties();
        properties.getAsr().setTokenTtlSeconds(300);
        AsrProxyTicketService service = new AsrProxyTicketService(redisTemplate, properties);

        AsrProxyTicketService.AsrProxyTicket ticket = service.issueTicket(42L);

        assertNotNull(ticket.getTicket());
        verify(valueOperations).set(anyString(), eq("42"), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    void claimTicket_shouldOnlySucceedOnFirstClaim() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("speech:asr:ticket:ticket-1:owner")).thenReturn("42");
        when(redisTemplate.getExpire("speech:asr:ticket:ticket-1:owner", TimeUnit.SECONDS)).thenReturn(120L);
        when(valueOperations.setIfAbsent("speech:asr:ticket:ticket-1:claimed", "1", 120L, TimeUnit.SECONDS))
                .thenReturn(true)
                .thenReturn(false);

        AsrProxyTicketService service = new AsrProxyTicketService(redisTemplate, new SpeechProperties());

        AsrProxyTicketService.ClaimedTicket firstClaim = service.claimTicket("ticket-1");
        AsrProxyTicketService.ClaimedTicket secondClaim = service.claimTicket("ticket-1");

        assertNotNull(firstClaim);
        assertEquals(42L, firstClaim.getUserId());
        assertNull(secondClaim);
    }
}

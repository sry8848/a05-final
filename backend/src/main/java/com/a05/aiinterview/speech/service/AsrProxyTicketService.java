package com.a05.aiinterview.speech.service;

import com.a05.aiinterview.speech.config.SpeechProperties;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AsrProxyTicketService {

    private static final String KEY_TICKET_OWNER = "speech:asr:ticket:%s:owner";
    private static final String KEY_TICKET_CLAIMED = "speech:asr:ticket:%s:claimed";

    private final StringRedisTemplate redisTemplate;
    private final SpeechProperties speechProperties;

    public AsrProxyTicket issueTicket(Long userId) {
        String ticket = UUID.randomUUID().toString();
        long ttlSeconds = Math.max(60L, speechProperties.getAsr().getTokenTtlSeconds());
        redisTemplate.opsForValue().set(ticketOwnerKey(ticket), String.valueOf(userId), ttlSeconds, TimeUnit.SECONDS);
        return AsrProxyTicket.builder()
                .ticket(ticket)
                .expiresAt(Instant.now().getEpochSecond() + ttlSeconds)
                .build();
    }

    public ClaimedTicket claimTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String owner = redisTemplate.opsForValue().get(ticketOwnerKey(ticket));
        if (owner == null || owner.isBlank()) {
            return null;
        }
        long ttlSeconds = Math.max(1L, redisTemplate.getExpire(ticketOwnerKey(ticket), TimeUnit.SECONDS));
        Boolean firstClaim = redisTemplate.opsForValue().setIfAbsent(
                ticketClaimedKey(ticket), "1", ttlSeconds, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(firstClaim)) {
            return null;
        }
        return ClaimedTicket.builder()
                .ticket(ticket)
                .userId(Long.parseLong(owner))
                .expiresAt(Instant.now().getEpochSecond() + ttlSeconds)
                .build();
    }

    private String ticketOwnerKey(String ticket) {
        return String.format(KEY_TICKET_OWNER, ticket);
    }

    private String ticketClaimedKey(String ticket) {
        return String.format(KEY_TICKET_CLAIMED, ticket);
    }

    @Data
    @Builder
    public static class AsrProxyTicket {
        private String ticket;
        private long expiresAt;
    }

    @Data
    @Builder
    public static class ClaimedTicket {
        private String ticket;
        private Long userId;
        private long expiresAt;
    }
}

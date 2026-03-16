package com.a05.aiinterview.speech;

import com.a05.aiinterview.speech.config.SpeechProperties;
import com.a05.aiinterview.speech.service.AliyunAsrWebSocketClient;
import com.a05.aiinterview.speech.service.AsrConstrainedCorrectionService;
import com.a05.aiinterview.speech.service.AsrProxySessionBridge;
import com.a05.aiinterview.speech.service.AsrProxyTicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

@Slf4j
public class AsrProxyWebSocketEndpoint extends Endpoint {

    private final AsrProxyTicketService asrProxyTicketService;
    private final AliyunAsrWebSocketClient aliyunAsrWebSocketClient;
    private final AsrConstrainedCorrectionService asrConstrainedCorrectionService;
    private final ObjectMapper objectMapper;
    private final SpeechProperties speechProperties;

    private volatile AsrProxySessionBridge bridge;

    public AsrProxyWebSocketEndpoint(
            AsrProxyTicketService asrProxyTicketService,
            AliyunAsrWebSocketClient aliyunAsrWebSocketClient,
            AsrConstrainedCorrectionService asrConstrainedCorrectionService,
            ObjectMapper objectMapper,
            SpeechProperties speechProperties) {
        this.asrProxyTicketService = asrProxyTicketService;
        this.aliyunAsrWebSocketClient = aliyunAsrWebSocketClient;
        this.asrConstrainedCorrectionService = asrConstrainedCorrectionService;
        this.objectMapper = objectMapper;
        this.speechProperties = speechProperties;
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        log.info("收到 ASR 代理 WebSocket 连接, sessionId={}, requestParams={}",
                session.getId(), session.getRequestParameterMap());
        String ticket = resolveTicket(session);
        AsrProxyTicketService.ClaimedTicket claimedTicket = asrProxyTicketService.claimTicket(ticket);
        if (claimedTicket == null) {
            reject(session, "invalid_ticket", "ASR ticket 无效、已过期或已被使用");
            return;
        }

        bridge = new AsrProxySessionBridge(
                session.getId(),
                claimedTicket,
                text -> session.getAsyncRemote().sendText(text),
                objectMapper,
                speechProperties,
                asrConstrainedCorrectionService);
        bridge.setAliyunClient(aliyunAsrWebSocketClient);
        log.info("ASR 代理 WebSocket ticket 校验通过, sessionId={}, userId={}",
                session.getId(), claimedTicket.getUserId());

        session.addMessageHandler(String.class, new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
                bridge.handleTextCommand(message);
            }
        });
        session.addMessageHandler(ByteBuffer.class, new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
                byte[] bytes = new byte[message.remaining()];
                message.get(bytes);
                bridge.handleBinaryAudio(bytes);
            }
        });
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        if (bridge != null) {
            bridge.close();
        }
    }

    @Override
    public void onError(Session session, Throwable thr) {
        log.error("ASR 代理 WebSocket 会话异常, sessionId={}", session == null ? "" : session.getId(), thr);
        if (bridge != null) {
            bridge.emitProxyError("websocket_closed", thr == null ? "unknown error" : thr.getMessage(), true);
            bridge.close();
        }
    }

    private String resolveTicket(Session session) {
        Map<String, List<String>> requestParams = session.getRequestParameterMap();
        if (requestParams == null) {
            return null;
        }
        List<String> tickets = requestParams.get("ticket");
        if (tickets == null || tickets.isEmpty()) {
            return null;
        }
        return tickets.get(0);
    }

    private void reject(Session session, String code, String message) {
        try {
            session.getBasicRemote().sendText(objectMapper.writeValueAsString(Map.of(
                    "type", "error",
                    "source", "proxy",
                    "code", code,
                    "message", message,
                    "taskId", "",
                    "retryable", false
            )));
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, message));
        } catch (IOException e) {
            log.warn("拒绝 ASR 代理连接失败, sessionId={}", session.getId(), e);
            try {
                session.close();
            } catch (IOException ignored) {
                // ignore secondary close failure
            }
        }
    }
}

package com.a05.aiinterview.speech;

import com.a05.aiinterview.speech.config.SpeechProperties;
import com.a05.aiinterview.speech.service.AliyunAsrWebSocketClient;
import com.a05.aiinterview.speech.service.AsrConstrainedCorrectionService;
import com.a05.aiinterview.speech.service.AsrProxyTicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import org.apache.tomcat.websocket.server.Constants;
import org.apache.tomcat.websocket.server.WsSci;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AsrProxyWebSocketMvcConfig implements ServletContextInitializer {

    private final AsrProxyTicketService asrProxyTicketService;
    private final AliyunAsrWebSocketClient aliyunAsrWebSocketClient;
    private final AsrConstrainedCorrectionService asrConstrainedCorrectionService;
    private final ObjectMapper objectMapper;
    private final SpeechProperties speechProperties;

    public AsrProxyWebSocketMvcConfig(
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
    public void onStartup(ServletContext servletContext) throws ServletException {
        log.info("准备注册 ASR 代理 WebSocket 上下文监听器, path=/asr/stream");
        servletContext.addListener(new ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent sce) {
                try {
                    registerEndpoint(sce.getServletContext());
                } catch (ServletException e) {
                    throw new IllegalStateException("注册 ASR 代理 WebSocket 端点失败", e);
                }
            }
        });
    }

    void registerEndpoint(ServletContext servletContext) throws ServletException {
        Object containerAttribute = servletContext.getAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
        if (!(containerAttribute instanceof ServerContainer)) {
            log.warn("ServletContext 初始化阶段未发现 ServerContainer，尝试手动初始化 Tomcat WebSocket 容器");
            new WsSci().onStartup(Set.of(), servletContext);
            containerAttribute = servletContext.getAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
        }
        if (!(containerAttribute instanceof ServerContainer serverContainer)) {
            throw new ServletException("未找到 Jakarta WebSocket ServerContainer，无法注册 ASR 代理端点");
        }

        ServerEndpointConfig endpointConfig = ServerEndpointConfig.Builder
                .create(AsrProxyWebSocketEndpoint.class, "/asr/stream")
                .configurator(new ServerEndpointConfig.Configurator() {
                    @Override
                    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
                        if (!AsrProxyWebSocketEndpoint.class.equals(endpointClass)) {
                            return super.getEndpointInstance(endpointClass);
                        }
                        return endpointClass.cast(new AsrProxyWebSocketEndpoint(
                                asrProxyTicketService,
                                aliyunAsrWebSocketClient,
                                asrConstrainedCorrectionService,
                                objectMapper,
                                speechProperties
                        ));
                    }
                })
                .build();

        try {
            serverContainer.addEndpoint(endpointConfig);
            log.info("ASR 代理 WebSocket 端点注册成功, path=/asr/stream, container={}",
                    serverContainer.getClass().getName());
        } catch (DeploymentException e) {
            throw new ServletException("注册 ASR 代理 WebSocket 端点失败", e);
        }
    }
}

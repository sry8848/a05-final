package com.a05.aiinterview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 异步任务配置类。
 * 主要功能：
 * 1. 提供 AsyncUncaughtExceptionHandler，捕获 @Async 方法中未处理的异常
 * 2. 避免异步异常被静默吞掉，确保异常能够被记录到日志
 *
 * <p>问题背景：Spring 的 @Async 方法如果抛出异常，默认情况下异常会被静默吞掉，
 * 不会打印到日志中。这导致像 PlannerOrchestrationService.runAsync() 这样的异步方法
 * 发生异常时，无法看到具体的错误信息。
 */
@Slf4j
@Configuration
public class AsyncConfiguration implements AsyncConfigurer {

    /**
     * 异步方法异常处理器。
     * 当 @Async 方法抛出未捕获的异常时，此方法会被调用。
     *
     * @param ex      抛出的异常
     * @param method  抛出异常的方法
     * @param params  方法的参数
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.error("异步方法执行异常 - 方法: {}, 参数: {}, 异常: {}",
                        method.getName(),
                        Arrays.toString(params),
                        ex.getMessage(),
                        ex);
            }
        };
    }
}

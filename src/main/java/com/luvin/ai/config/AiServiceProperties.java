package com.luvin.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 서비스(FastAPI) 연동 설정.
 * base-url은 반드시 환경변수 AI_SERVICE_BASE_URL로 주입한다 (app.ai.base-url=${AI_SERVICE_BASE_URL}).
 * 코드에 주소를 고정하지 않는다 (요구사항 3.1).
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiServiceProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        PollingProperties polling,
        RetryProperties retry
) {
    public AiServiceProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
        if (polling == null) {
            polling = new PollingProperties(Duration.ofSeconds(1), Duration.ofSeconds(5));
        }
        if (retry == null) {
            retry = new RetryProperties(3, Duration.ofSeconds(1), Duration.ofSeconds(8));
        }
    }

    /**
     * job polling backoff: 1초부터 시작해 최대 5초까지 backoff (요구사항 5.3).
     */
    public record PollingProperties(Duration initialInterval, Duration maxInterval) {
    }

    /**
     * 5xx/timeout에 대한 제한적 재시도 backoff (요구사항 6절).
     */
    public record RetryProperties(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
    }
}

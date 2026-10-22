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
        RetryProperties retry,
        StreamProperties stream
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
        if (stream == null) {
            stream = new StreamProperties(Duration.ofMinutes(10), Duration.ofSeconds(15), 200);
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

    /**
     * job SSE 중계(GET /api/ai/jobs/{jobId}/events).
     * maxDuration이 지나면 앱 연결을 닫는다 — 앱은 Last-Event-ID로 다시 붙거나 polling으로 돌아간다.
     * heartbeatInterval마다 SSE 주석을 보내 ALB idle timeout(기본 60초)에 끊기지 않게 하고, 앱이 떠난 것도 이때 알아챈다.
     * maxConcurrent는 서버 한 대가 동시에 열어두는 스트림 수 상한이다(초과 시 503).
     */
    public record StreamProperties(Duration maxDuration, Duration heartbeatInterval, int maxConcurrent) {
    }
}

package com.luvin.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 장면 블록 회차 생성(FastAPI story endpoint) 설정.
 *
 * @param client           stub(기본) | http. FastAPI 계약이 확정되기 전에는 stub으로 흐름만 확인한다
 * @param readTimeout      생성 호출 응답 대기 시간. 집필 호출은 일반 호출(10초)보다 오래 걸린다
 * @param generationStaleAfter GENERATING 상태가 이보다 오래되면(서버 재시작 등으로 멈춘 것으로 보고) 다시 생성한다
 */
@ConfigurationProperties(prefix = "app.ai.story")
public record AiStoryProperties(String client, Duration readTimeout, Duration generationStaleAfter) {
    public AiStoryProperties {
        if (client == null || client.isBlank()) {
            client = "stub";
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(60);
        }
        if (generationStaleAfter == null) {
            generationStaleAfter = Duration.ofMinutes(3);
        }
    }
}

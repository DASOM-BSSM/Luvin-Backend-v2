package com.luvin.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 장면 블록 회차 생성(FastAPI story endpoint)과 회차 열기 설정.
 *
 * @param client           stub(기본) | http. FastAPI 계약이 확정되기 전에는 stub으로 흐름만 확인한다
 * @param readTimeout      생성 호출 응답 대기 시간. 집필 호출은 일반 호출(10초)보다 오래 걸린다
 * @param generationStaleAfter GENERATING 상태가 이보다 오래되면(서버 재시작 등으로 멈춘 것으로 보고) 다시 생성한다
 * @param episodeTokenCost 4~10화를 처음 열 때 쓰는 토큰 수(기본 1). 팀이 테스트하며 조정한다
 * @param tokenChargeEnabled false면 회차를 열 때 토큰을 차감하지 않는다(개발 서버 테스트용, 기본 true)
 */
@ConfigurationProperties(prefix = "app.ai.story")
public record AiStoryProperties(String client, Duration readTimeout, Duration generationStaleAfter,
                                Integer episodeTokenCost, Boolean tokenChargeEnabled) {
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
        if (episodeTokenCost == null) {
            episodeTokenCost = 1;
        }
        if (tokenChargeEnabled == null) {
            tokenChargeEnabled = true;
        }
    }
}

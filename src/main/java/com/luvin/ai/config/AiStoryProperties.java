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
 * @param generationMode   jobs(기본) | sync. jobs면 http 클라이언트가 FastAPI 비동기 생성 job(POST /luvin/generation-jobs)으로
 *                         접수하고 끝날 때까지 조회한다. sync면 기존 동기 endpoint(POST /luvin/{task})를 부른다
 * @param jobPollInterval  job 상태 조회 간격(기본 1초)
 * @param jobWaitTimeout   job 하나를 기다리는 최대 시간(기본 3분). AI 쪽 전체 제한(최대 55초)과 대기열, worker 유실 판정(90초
 *                         lease)을 넘는 값이다. 넘으면 이번 시도는 실패로 보고 job은 AI에 그대로 둔다
 * @param resumeOnStartup  기동 때 끝나지 않은 회차 생성·완료를 이어서 진행한다(기본 true). 같은 키로 다시 접수하므로
 *                         이미 접수한 job을 이어서 본다
 * @param prefetchNextEpisode 다음 회차를 앱이 요청하기 전에 미리 생성한다(기본 true). 2·3화는 이전 회차 완료 직후, 4~10화는
 *                         회차를 연(unlock) 직후에 시작한다
 */
@ConfigurationProperties(prefix = "app.ai.story")
public record AiStoryProperties(String client, Duration readTimeout, Duration generationStaleAfter,
                                Integer episodeTokenCost, Boolean tokenChargeEnabled, String generationMode,
                                Duration jobPollInterval, Duration jobWaitTimeout, Boolean resumeOnStartup,
                                Boolean prefetchNextEpisode) {

    public static final String MODE_JOBS = "jobs";

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
        if (generationMode == null || generationMode.isBlank()) {
            generationMode = MODE_JOBS;
        }
        if (jobPollInterval == null) {
            jobPollInterval = Duration.ofSeconds(1);
        }
        if (jobWaitTimeout == null) {
            jobWaitTimeout = Duration.ofMinutes(3);
        }
        if (resumeOnStartup == null) {
            resumeOnStartup = true;
        }
        if (prefetchNextEpisode == null) {
            prefetchNextEpisode = true;
        }
    }

    public boolean usesJobs() {
        return MODE_JOBS.equals(generationMode);
    }
}

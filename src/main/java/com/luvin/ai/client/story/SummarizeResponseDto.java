package com.luvin.ai.client.story;

import java.util.List;
import java.util.UUID;

/**
 * 정리 결과. recentMemory는 이번 회차 주요 장면 한 줄로, Spring이 최근 3회차만 남긴다.
 * knownFacts/openThreads는 갱신된 전체 목록이고, affinityDelta(-5~+5)는 Spring이 범위를 검사해 반영한다.
 */
public record SummarizeResponseDto(String episodeSummary, List<RelationUpdate> relations) {

    public record RelationUpdate(UUID breadId, String memorySummary, String recentMemory, List<String> knownFacts,
                                 List<String> openThreads, String statusText, Integer affinityDelta) {
    }
}

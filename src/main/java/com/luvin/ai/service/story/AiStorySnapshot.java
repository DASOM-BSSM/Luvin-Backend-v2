package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiSeasonSetting;
import com.luvin.ai.domain.AiSeasonStatus;
import com.luvin.ai.domain.AiStoryContentStatus;
import com.luvin.ai.domain.AiStoryRoute;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 한 시즌의 장면 블록 회차 상태를 트랜잭션 안에서 한 번에 읽어 둔 값. FastAPI 호출(트랜잭션 밖)에서 lazy 연관관계를
 * 건드리지 않도록 엔티티 대신 이 값을 넘긴다.
 */
public record AiStorySnapshot(
        Long seasonPk,
        UUID seasonId,
        Long memberId,
        int seasonNumber,
        AiSeasonStatus status,
        AiSeasonSetting setting,
        Integer currentOrder,
        AiStoryRoute route,
        UUID romancePartnerId,
        List<AiStoryCastMember> cast,
        List<Relation> relations,
        List<Choice> choices,
        List<Content> contents
) {

    public record Relation(UUID breadId, int affinity, int rejectCount, String statusText, String memorySummary,
                           List<String> recentMemories, List<String> knownFacts, List<String> openThreads) {
    }

    public record Choice(int pointId, int episodeOrder, UUID breadId, boolean accepted, String reasonCode,
                         String resultBlocksJson) {
    }

    public record Content(Long id, int order, AiStoryContentStatus status, AiStoryRoute route, String errorCode,
                          String introJson, String stageIntroJson, String blocksJson, String summaryJson,
                          LocalDateTime requestedAt, boolean completed) {
    }

    public boolean isStarted() {
        return setting != null;
    }

    public Optional<Content> content(int order) {
        return contents.stream().filter(c -> c.order() == order).findFirst();
    }

    public Optional<Choice> choice(int pointId) {
        return choices.stream().filter(c -> c.pointId() == pointId).findFirst();
    }

    public Optional<Relation> relation(UUID breadId) {
        return relations.stream().filter(r -> r.breadId().equals(breadId)).findFirst();
    }

    public AiStoryCastMember me() {
        return cast.stream().filter(AiStoryCastMember::isMe).findFirst().orElseThrow();
    }

    public Optional<AiStoryCastMember> member(UUID breadId) {
        return cast.stream().filter(c -> c.breadId().equals(breadId)).findFirst();
    }
}

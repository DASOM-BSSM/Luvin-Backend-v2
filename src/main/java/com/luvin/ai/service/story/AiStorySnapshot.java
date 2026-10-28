package com.luvin.ai.service.story;

import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.domain.AiSeasonSetting;
import com.luvin.ai.domain.AiSeasonStatus;
import com.luvin.ai.domain.AiSpeechLevel;
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
        List<Content> contents,
        /** 토큰을 써서 연 4~10화(1~3화는 무료라 들어 있지 않다). */
        List<Integer> unlockedOrders
) {

    public record Relation(UUID breadId, int affinity, int rejectCount, String statusText, String memorySummary,
                           List<String> recentMemories, List<String> knownFacts, List<StoryTypes.Thread> openThreads,
                           String choiceMemory, AiSpeechLevel speechLevel) {
    }

    public record Choice(int pointId, int episodeOrder, UUID breadId, boolean accepted, String reasonCode,
                         String resultBlocksJson) {
    }

    public record Content(Long id, int order, AiStoryContentStatus status, AiStoryRoute route, String errorCode,
                          String introJson, String stageIntroJson, String blocksJson, String summaryJson,
                          LocalDateTime requestedAt, boolean completed, LocalDateTime completionRequestedAt,
                          String completionErrorCode) {

        /** 완료를 시작했고 아직 끝나지도, 실패하지도 않았다. */
        public boolean completing() {
            return !completed && completionRequestedAt != null && completionErrorCode == null;
        }
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

    /** 계약 빵 id(me, avoidant, …) → 시즌 캐릭터. */
    public Optional<AiStoryCastMember> memberByContractId(String contractId) {
        return cast.stream().filter(c -> c.castRole().contractId().equals(contractId)).findFirst();
    }
}

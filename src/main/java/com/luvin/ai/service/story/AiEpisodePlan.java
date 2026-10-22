package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiSceneBlockType;
import com.luvin.ai.domain.AiSeasonSetting;
import com.luvin.ai.domain.AiStoryRoute;

import java.util.List;
import java.util.UUID;

/**
 * 설계 단계(코드) 결과. 집필 단계(FastAPI)는 이 블록 순서·참여 빵을 그대로 따라 대사만 채운다.
 *
 * @param referenceEpisode 참고 줄거리로 넣을 기존 본편 회차 번호. 없으면 null
 */
public record AiEpisodePlan(
        int order,
        AiSeasonSetting setting,
        String commonEvent,
        String settingScene,
        AiStoryRoute route,
        Integer referenceEpisode,
        List<String> openThreads,
        List<BlockPlan> blocks
) {

    /**
     * @param messengerKind messenger 블록만: "direct" | "group"
     * @param choice        choice 블록만
     * @param focusBreadId  pair/messenger/interview/choice_result 블록에서 장면의 상대 빵. 혼자인 장면이면 null
     * @param shortReply    choice_result 블록만: 회피형이 ②에서 짧게 수락하는 장면
     */
    public record BlockPlan(String id, AiSceneBlockType type, List<UUID> participantIds, String messengerKind,
                            ChoicePlan choice, UUID focusBreadId, boolean shortReply) {
    }

    public record ChoicePlan(int pointId, String question, List<UUID> candidateIds) {
    }

    /** 회차 집필 호출로 대사를 받는 블록(choice는 Spring이 채우고, interview는 인터뷰 호출로 따로 받는다). */
    public List<BlockPlan> episodeBlocks() {
        return blocks.stream()
                .filter(b -> b.type() != AiSceneBlockType.CHOICE && b.type() != AiSceneBlockType.INTERVIEW)
                .toList();
    }

    public List<BlockPlan> interviewBlocks() {
        return blocks.stream().filter(b -> b.type() == AiSceneBlockType.INTERVIEW).toList();
    }
}

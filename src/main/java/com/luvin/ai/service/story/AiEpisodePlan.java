package com.luvin.ai.service.story;

import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.domain.AiSceneBlockType;
import com.luvin.ai.domain.AiSeasonSetting;
import com.luvin.ai.domain.AiStoryRoute;

import java.util.List;
import java.util.UUID;

/**
 * 설계 단계(코드) 결과. 집필 단계(FastAPI)는 이 블록 순서·참여 빵을 그대로 따라 대사만 채운다.
 *
 * @param referencePlot 참고 줄거리로 넣을 기존 본편 줄거리 텍스트. 없으면 null
 */
public record AiEpisodePlan(
        int order,
        AiSeasonSetting setting,
        String commonEvent,
        String settingScene,
        AiStoryRoute route,
        String referencePlot,
        List<StoryTypes.Thread> openThreads,
        List<BlockPlan> blocks
) {

    /**
     * @param messengerKind messenger 블록만: "direct" | "group"
     * @param roomName      messenger 블록만: 방 이름(direct면 상대 이름, group이면 배경별 단톡방 이름)
     * @param choice        choice 블록만
     * @param focusBreadId  pair/messenger/interview/choice_result 블록에서 장면의 상대 빵. 혼자인 장면이면 null
     *                      (pair면 FastAPI 설계의 pairTarget = 이 빵의 계약 id, null이면 "alone")
     * @param shortReply    choice_result 블록만: 회피형이 ②에서 짧게 수락하는 장면
     */
    public record BlockPlan(String id, AiSceneBlockType type, List<UUID> participantIds, String messengerKind,
                            String roomName, ChoicePlan choice, UUID focusBreadId, boolean shortReply) {
    }

    public record ChoicePlan(int pointId, String question, List<UUID> candidateIds) {
    }

    /**
     * 회차 집필 호출(5-2)의 design. choice도 포함한다(FastAPI가 후보별 기억 한 줄을 채운다).
     * interview는 넣지 않고 인터뷰 호출(5-4)로 따로 받는다.
     */
    public List<BlockPlan> episodeBlocks() {
        return blocks.stream().filter(b -> b.type() != AiSceneBlockType.INTERVIEW).toList();
    }

    public List<BlockPlan> interviewBlocks() {
        return blocks.stream().filter(b -> b.type() == AiSceneBlockType.INTERVIEW).toList();
    }
}

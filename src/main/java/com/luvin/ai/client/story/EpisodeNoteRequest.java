package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.ChoiceRecord;
import com.luvin.ai.client.story.StoryTypes.GenerationContext;

import java.util.List;

/**
 * 5-6 POST /luvin/episode-note. choice는 이 회차의 선택(없으면 null).
 * sender는 Spring이 정한 쪽지 발신 빵(계약 7장 "회차 쪽지 발신자"), previousNoteOpenings는 이번 시즌에 이미 보낸
 * 쪽지들의 첫 문장(같은 시작을 반복하지 않게).
 */
public record EpisodeNoteRequest(GenerationContext context, int order, String settingScene, String episodeSummary,
                                 ChoiceRecord choice, List<Relation> relations, Sender sender,
                                 List<String> previousNoteOpenings) {

    public record Relation(String breadId, String statusText, String recentMemory) {
    }

    /**
     * attachmentStyle은 말투를 맞추는 데만 쓰고 쪽지 문구에는 쓰지 않는다(계약 5-6). speechLevel(formal | casual)은 이
     * 회차를 시작할 때의 관계 말투다 — 같은 회차 정리 단계가 바꾼 말투는 다음 회차부터 쓴다(reconstruction.v22).
     */
    public record Sender(String breadId, String speechLevel, String name, String attachmentStyle, String statusText) {
    }
}

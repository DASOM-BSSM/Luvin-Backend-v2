package com.luvin.ai.client.story;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.luvin.ai.client.story.StoryTypes.ChoiceRecord;
import com.luvin.ai.client.story.StoryTypes.EpisodeSummary;
import com.luvin.ai.client.story.StoryTypes.GenerationContext;

import java.util.List;

/**
 * 5-6 POST /luvin/episode-note. choice는 이 회차의 선택(없으면 null).
 * sender는 Spring이 정한 쪽지 발신 빵(계약 7장 "회차 쪽지 발신자"), previousNoteOpenings는 이번 시즌에 이미 보낸
 * 쪽지들의 첫 문장(같은 시작을 반복하지 않게).
 * episodeSummaries는 10화(시즌 종료 쪽지)에만 1~10화 요약을 담고, 그 외 회차에는 필드가 빠진다(AI 문서 P6).
 */
public record EpisodeNoteRequest(GenerationContext context, int order, String settingScene, String episodeSummary,
                                 ChoiceRecord choice, List<Relation> relations, Sender sender,
                                 List<String> previousNoteOpenings,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) List<EpisodeSummary> episodeSummaries) {

    public record Relation(String breadId, String statusText, String recentMemory) {
    }

    /** castRole: me | friend | maker | candidate, attachmentStyle: 후보면 avoidant/anxious/secure, 그 외 none. */
    public record Sender(String breadId, String name, String castRole, String attachmentStyle, String statusText) {
    }
}

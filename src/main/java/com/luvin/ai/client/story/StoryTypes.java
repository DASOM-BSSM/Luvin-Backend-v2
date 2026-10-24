package com.luvin.ai.client.story;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 러빈지옥 공통 데이터 계약(Spring ↔ FastAPI, 5장)의 공통 타입. 필드 이름·enum 문자열은 계약 문서 그대로다
 * (camelCase로 직렬화 — aiStoryObjectMapper). 빵 id는 UUID가 아니라 역할 고정 id(me, avoidant, anxious,
 * secure, friend, maker)이고, Spring이 시즌 캐릭터 UUID와 서로 바꾼다.
 */
public final class StoryTypes {

    public static final String NARRATOR = "narrator";
    public static final String ALONE = "alone";
    public static final String KIND_CHAT = "chat";
    public static final String KIND_MONOLOGUE = "monologue";
    public static final String KIND_NARRATION = "narration";
    public static final List<String> MESSAGE_KINDS = List.of(KIND_CHAT, KIND_MONOLOGUE, KIND_NARRATION);

    private StoryTypes() {
    }

    /** speakerId는 빵 id 또는 "narrator". read/typing/deletedAfterSend는 messenger 전용. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(String id, String speakerId, String text, String kind, Boolean read, Boolean typing,
                          Boolean deletedAfterSend) {
    }

    /** 5-0 GenerationContext.cast: 3-3 CastMember + gender(사용자 성별 형식). description은 me면 null. */
    public record CastMember(String breadId, String name, String breadType, String role, String description,
                             String gender) {
    }

    public record Thread(String id, String text) {
    }

    /** 5-0 RelationRecord. 호감은 숫자 대신 구간(low/mid/high)만 넘긴다. */
    public record RelationRecord(String breadId, String affinityLevel, String statusText, String memorySummary,
                                 List<String> recentMemories, List<String> knownFacts, List<Thread> openThreads) {
    }

    /** 3-4 ChoiceRecord. breadId null = 혼자 있기. */
    public record ChoiceRecord(int pointId, int episodeOrder, String breadId, boolean accepted) {
    }

    /** 5-7의 choices 항목: ChoiceRecord + 거절 사유(거절이 아니면 null). */
    public record ChoiceRecordWithReason(int pointId, int episodeOrder, String breadId, boolean accepted,
                                         String reason) {
    }

    public record ChoiceCandidate(String breadId, String memory) {
    }

    /** 회차 요약 하나(5-7 episodeSummaries, 10화 쪽지의 episodeSummaries). */
    public record EpisodeSummary(int order, String summary) {
    }

    /** 5-0 GenerationContext. */
    public record GenerationContext(String setting, String audience, String ageGroup, List<CastMember> cast,
                                    LearnedProfile learnedProfile) {
    }

    public record LearnedProfile(String summary, List<String> recentAnswers, List<Feedback> feedbacks,
                                 List<String> likedMessages) {
    }

    public record Feedback(int order, String messageText, String text) {
    }

    /** 5-0 BlockDesign. pairTarget/short는 pair, messenger는 messenger, pointId/candidateIds는 choice에만. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BlockDesign(String id, String type, List<String> participantIds, String pairTarget,
                              @JsonProperty("short") Boolean shortReply, Messenger messenger, Integer pointId,
                              List<String> candidateIds) {
    }

    public record Messenger(String kind, String roomName) {
    }

    /** 5-0 GeneratedBlock. id는 BlockDesign.id와 같다. choice는 candidates의 memory만 채운다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GeneratedBlock(String id, List<Message> messages, String transitionText,
                                 List<ChoiceCandidate> candidates) {
    }

    public record Intro(String title, String situation) {
    }

    /** 3-1 Participant(빵 id 기준). role은 이 배경에서의 역할. */
    public record Participant(String breadId, String name, String breadType, String role) {
    }

    /** 3-2 SceneBlock(빵 id 기준). 정리 단계(5-5) 입력으로 최종 재생된 블록을 보낼 때 쓴다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SceneBlock(String id, String type, List<Participant> participants, List<Message> messages,
                             String transitionText, Messenger messenger, ChoiceBlock choice, ChoiceResult result,
                             String interviewBreadId) {
    }

    public record ChoiceBlock(int pointId, String question, List<ChoiceCandidate> candidates) {
    }

    public record ChoiceResult(int pointId, String breadId, boolean accepted, List<Message> messages,
                               List<SceneBlock> fallbackBlocks) {
    }
}

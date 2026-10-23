package com.luvin.ai.client.story;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 모든 장면 블록 회차 생성 호출에 함께 보내는 시즌 맥락. 1화 무대 소개로 받은 배경별 역할·소개(stageRole/stageDescription)도
 * 출연진에 담아 이후 모든 호출에 보낸다. 필드 상세는 AI 문서(luvin_에피소드_재구성_AI_프롬프트.md)와 합의해 확정한다.
 */
public record StoryContextDto(
        String setting,
        String audience,
        String ageBand,
        Integer userAge,
        List<CastMember> cast,
        List<Relation> relations,
        List<ChoiceRecord> choices,
        LearningData learningData
) {

    public record CastMember(UUID breadId, String name, String castRole, String attachmentStyle, String gender,
                             boolean isMe, String stageRole, String stageDescription) {
    }

    public record Relation(UUID breadId, int affinity, int rejectCount, String statusText, String memorySummary,
                           List<String> recentMemories, List<String> knownFacts, List<String> openThreads) {
    }

    /** reasonCode는 10화 인터뷰룸 생성 재료다. */
    public record ChoiceRecord(int pointId, int episodeOrder, UUID breadId, boolean accepted, String reasonCode) {
    }

    /** 내 빵 학습 데이터. 설문 성향 점수와 최근 감정일기·매시간 질문 답변. */
    public record LearningData(String personalityType, Object traits, List<DailyAnswer> dailyAnswers,
                               List<DiaryExcerpt> diaries) {
    }

    public record DailyAnswer(String question, String answer, LocalDateTime answeredAt) {
    }

    public record DiaryExcerpt(String title, String content, OffsetDateTime createdAt) {
    }
}

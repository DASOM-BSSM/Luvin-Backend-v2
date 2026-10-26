package com.luvin.ai.service.story;

import com.luvin.ai.client.story.ChoiceResultResponse;
import com.luvin.ai.client.story.EpisodeNoteResponse;
import com.luvin.ai.client.story.EpisodeResponse;
import com.luvin.ai.client.story.InterviewResponse;
import com.luvin.ai.client.story.SeasonReportResponse;
import com.luvin.ai.client.story.StageIntroResponse;
import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.client.story.SummarizeResponse;
import com.luvin.ai.domain.AiSceneBlockType;
import com.luvin.ai.domain.AiSpeechLevel;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FastAPI 생성 응답(공통 데이터 계약 5장)을 저장하기 전에 Spring에서 한 번 더 검사한다. 요청한 설계(블록 id·참여 빵)를
 * 벗어난 응답, 빈 대사, 계약의 길이 제한 초과는 {@link AiStoryInvalidResponseException}으로 막고 호출부가 1회 재시도한다.
 * 빵 id는 계약 id(me, avoidant, …) 기준이다.
 */
public final class AiStoryResponseValidator {

    /** 블록 설계 하나: 기대하는 블록 종류와 그 블록에서 말할 수 있는 빵(계약 id). choice면 후보 빵. */
    public record ExpectedBlock(String id, AiSceneBlockType type, Set<String> speakers, Set<String> candidates) {
    }

    static final int INTRO_TITLE_MAX = 8;
    static final int EPISODE_NAME_MAX = 20;
    static final int INTRO_SITUATION_MAX = 200;
    static final int TRANSITION_MAX = 15;
    static final int MESSAGE_MAX = 500;
    static final int MESSAGES_PER_BLOCK_MAX = 60;
    static final int STAGE_ROLE_MAX = 10;
    static final int STAGE_DESCRIPTION_MAX = 25;
    static final int MEMORY_MAX = 20;
    static final int RECENT_MEMORY_MAX = 200;
    static final int STATUS_TEXT_MAX = 6;
    static final int NOTE_MAX = 2000;

    private AiStoryResponseValidator() {
    }

    public static void validateStageIntro(StageIntroResponse response, Collection<String> castIds) {
        require(response != null && response.cast() != null, "stage intro cast가 없습니다.");
        Set<String> seen = new HashSet<>();
        for (StageIntroResponse.Cast cast : response.cast()) {
            require(castIds.contains(cast.breadId()), "출연진에 없는 breadId: " + cast.breadId());
            requireText(cast.role(), STAGE_ROLE_MAX, "cast role");
            if ("me".equals(cast.breadId())) {
                require(cast.description() == null || cast.description().length() <= STAGE_DESCRIPTION_MAX,
                        "me description 길이 초과");
            } else {
                requireText(cast.description(), STAGE_DESCRIPTION_MAX, "cast description");
            }
            seen.add(cast.breadId());
        }
        require(seen.containsAll(castIds), "무대 소개에 빠진 출연진이 있습니다.");
    }

    public static void validateEpisode(EpisodeResponse response, List<ExpectedBlock> expected) {
        require(response != null && response.intro() != null, "intro가 없습니다.");
        requireText(response.intro().title(), INTRO_TITLE_MAX, "intro title");
        requireText(response.intro().episodeName(), EPISODE_NAME_MAX, "intro episodeName");
        requireText(response.intro().situation(), INTRO_SITUATION_MAX, "intro situation");
        require(response.blocks() != null, "blocks가 없습니다.");
        Map<String, StoryTypes.GeneratedBlock> byId = response.blocks().stream()
                .collect(Collectors.toMap(StoryTypes.GeneratedBlock::id, Function.identity(), (a, b) -> {
                    throw new AiStoryInvalidResponseException("중복된 block id: " + a.id());
                }));
        require(byId.keySet().equals(expected.stream().map(ExpectedBlock::id).collect(Collectors.toSet())),
                "요청한 설계와 블록 구성이 다릅니다: " + byId.keySet());
        for (ExpectedBlock block : expected) {
            StoryTypes.GeneratedBlock generated = byId.get(block.id());
            switch (block.type()) {
                case TRANSITION -> requireText(generated.transitionText(), TRANSITION_MAX, "transitionText");
                case CHOICE -> validateCandidates(generated.candidates(), block);
                default -> validateMessages(generated.messages(), block.speakers(), block.id());
            }
        }
    }

    public static void validateChoiceResult(ChoiceResultResponse response, Set<String> speakers, boolean accepted,
                                            String friendId) {
        require(response != null, "선택 결과 응답이 없습니다.");
        validateMessages(response.messages(), speakers, "choice_result");
        List<ChoiceResultResponse.FallbackBlock> fallbacks =
                response.fallbackBlocks() == null ? List.of() : response.fallbackBlocks();
        require(!accepted || fallbacks.isEmpty(), "수락인데 대체 장면이 있습니다.");
        Set<String> ids = new HashSet<>();
        for (ChoiceResultResponse.FallbackBlock fallback : fallbacks) {
            require(fallback.id() != null && ids.add(fallback.id()), "대체 장면 id가 비었거나 중복입니다.");
            require(StoryTypes.ALONE.equals(fallback.pairTarget()) || friendId.equals(fallback.pairTarget()),
                    "대체 장면 pairTarget은 alone | friend: " + fallback.pairTarget());
            Set<String> fallbackSpeakers = StoryTypes.ALONE.equals(fallback.pairTarget())
                    ? Set.of("me") : Set.of("me", friendId);
            validateMessages(fallback.messages(), fallbackSpeakers, fallback.id());
        }
    }

    public static void validateInterview(InterviewResponse response, Collection<String> interviewees) {
        require(response != null && response.interviews() != null, "인터뷰 응답이 없습니다.");
        Set<String> seen = new HashSet<>();
        for (InterviewResponse.Interview interview : response.interviews()) {
            require(interviewees.contains(interview.breadId()) && seen.add(interview.breadId()),
                    "요청하지 않았거나 중복된 인터뷰: " + interview.breadId());
            validateMessages(interview.messages(), Set.of(interview.breadId()), "interview " + interview.breadId());
        }
        require(seen.containsAll(interviewees), "빠진 인터뷰가 있습니다.");
    }

    public static void validateSummary(SummarizeResponse response, Collection<String> relationIds) {
        require(response != null, "정리 응답이 없습니다.");
        requireText(response.episodeSummary(), NOTE_MAX, "episodeSummary");
        require(response.relations() != null, "relations가 없습니다.");
        for (SummarizeResponse.RelationUpdate update : response.relations()) {
            require(relationIds.contains(update.breadId()), "관계 기록에 없는 breadId: " + update.breadId());
            require(update.recentMemory() == null || update.recentMemory().length() <= RECENT_MEMORY_MAX,
                    "recentMemory 길이 초과");
            require(update.choiceMemory() == null || update.choiceMemory().length() <= MEMORY_MAX,
                    "choiceMemory 길이 초과");
            require(update.speechLevel() == null || AiSpeechLevel.fromWire(update.speechLevel()) != null,
                    "speechLevel은 formal | casual: " + update.speechLevel());
            require(update.statusText() == null || update.statusText().length() <= STATUS_TEXT_MAX,
                    "statusText 길이 초과");
            if (update.threadsAdd() != null) {
                update.threadsAdd().forEach(t -> require(t.id() != null && !t.id().isBlank()
                        && t.text() != null && !t.text().isBlank(), "떡밥 id·text가 비었습니다."));
            }
        }
    }

    public static void validateNote(EpisodeNoteResponse response, Collection<String> castIds) {
        require(response != null, "쪽지 응답이 없습니다.");
        requireText(response.body(), NOTE_MAX, "note body");
        require(response.finalPartnerId() == null || castIds.contains(response.finalPartnerId()),
                "출연진에 없는 쪽지 발신 빵: " + response.finalPartnerId());
    }

    public static void validateReport(SeasonReportResponse response, Collection<String> castIds) {
        require(response != null, "리포트 응답이 없습니다.");
        requireText(response.tendency(), 150, "tendency");
        requireText(response.feedback(), 150, "feedback");
        require(response.finalPartnerId() == null || castIds.contains(response.finalPartnerId()),
                "출연진에 없는 최종 상대: " + response.finalPartnerId());
        require(response.strengths() != null && !response.strengths().isEmpty() && response.strengths().size() <= 3,
                "strengths는 1~3개여야 합니다.");
        require(response.weaknesses() != null && !response.weaknesses().isEmpty() && response.weaknesses().size() <= 3,
                "weaknesses는 1~3개여야 합니다.");
        require(response.highlights() != null && response.highlights().episode2() != null
                && response.highlights().episode4() != null, "highlights가 없습니다.");
    }

    private static void validateCandidates(List<StoryTypes.ChoiceCandidate> candidates, ExpectedBlock block) {
        require(candidates != null, block.id() + " 후보 기억이 없습니다.");
        Set<String> ids = new HashSet<>();
        for (StoryTypes.ChoiceCandidate candidate : candidates) {
            require(block.candidates().contains(candidate.breadId()) && ids.add(candidate.breadId()),
                    block.id() + " 후보가 아니거나 중복: " + candidate.breadId());
            requireText(candidate.memory(), MEMORY_MAX, "candidate memory");
        }
        require(ids.equals(block.candidates()), block.id() + " 후보 기억이 빠졌습니다.");
    }

    /** 대사의 화자는 그 블록 참여 빵이거나 narrator, kind는 chat | monologue | narration. */
    private static void validateMessages(List<StoryTypes.Message> messages, Set<String> speakers, String where) {
        require(messages != null && !messages.isEmpty() && messages.size() <= MESSAGES_PER_BLOCK_MAX,
                where + " 대사 수가 올바르지 않습니다.");
        for (StoryTypes.Message message : messages) {
            require(StoryTypes.NARRATOR.equals(message.speakerId()) || speakers.contains(message.speakerId()),
                    where + " 블록 참여 빵이 아닌 화자: " + message.speakerId());
            require(StoryTypes.MESSAGE_KINDS.contains(message.kind()), where + " 알 수 없는 kind: " + message.kind());
            requireText(message.text(), MESSAGE_MAX, "message text");
        }
    }

    private static void requireText(String value, int max, String field) {
        require(value != null && !value.isBlank(), field + "이(가) 비어 있습니다.");
        require(value.length() <= max, field + " 길이 초과(" + value.length() + " > " + max + ")");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AiStoryInvalidResponseException(message);
        }
    }
}

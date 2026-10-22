package com.luvin.ai.service.story;

import com.luvin.ai.client.dto.LetterResponseDto;
import com.luvin.ai.client.dto.ReportResponseDto;
import com.luvin.ai.client.story.EpisodeWriteResponseDto;
import com.luvin.ai.client.story.StageIntroResponseDto;
import com.luvin.ai.client.story.StoryWrittenBlockDto;
import com.luvin.ai.client.story.SummarizeResponseDto;
import com.luvin.ai.domain.AiSceneBlockType;
import com.luvin.ai.service.story.AiEpisodePlan.BlockPlan;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FastAPI 생성 응답을 저장하기 전에 Spring에서 한 번 더 검사한다. 요청한 블록 구성(id·참여 빵)을 벗어난 응답,
 * 빈 대사, 길이 초과는 {@link AiStoryInvalidResponseException}으로 막고 호출부가 1회 재시도한다.
 */
public final class AiStoryResponseValidator {

    static final int INTRO_TITLE_MAX = 40;
    static final int INTRO_SITUATION_MAX = 300;
    static final int TRANSITION_MAX = 60;
    static final int ROOM_NAME_MAX = 40;
    static final int MESSAGE_MAX = 500;
    static final int MESSAGES_PER_BLOCK_MAX = 60;
    static final int STAGE_ROLE_MAX = 40;
    static final int STAGE_DESCRIPTION_MAX = 200;
    static final int MEMORY_MAX = 200;
    static final int NOTE_MAX = 2000;

    private AiStoryResponseValidator() {
    }

    public static void validateStageIntro(StageIntroResponseDto response, Collection<UUID> castIds) {
        require(response != null && response.cast() != null, "stage intro cast가 없습니다.");
        Set<UUID> seen = new HashSet<>();
        for (StageIntroResponseDto.CastIntro intro : response.cast()) {
            require(intro.breadId() != null && castIds.contains(intro.breadId()), "출연진에 없는 breadId: " + intro.breadId());
            requireText(intro.role(), STAGE_ROLE_MAX, "cast role");
            requireText(intro.description(), STAGE_DESCRIPTION_MAX, "cast description");
            seen.add(intro.breadId());
        }
        require(seen.containsAll(castIds), "무대 소개에 빠진 출연진이 있습니다.");
    }

    public static void validateEpisode(EpisodeWriteResponseDto response, List<BlockPlan> planned) {
        require(response != null && response.intro() != null, "intro가 없습니다.");
        requireText(response.intro().title(), INTRO_TITLE_MAX, "intro title");
        requireText(response.intro().situation(), INTRO_SITUATION_MAX, "intro situation");
        validateBlocks(response.blocks(), planned);
    }

    /** 요청한 블록과 id가 정확히 같아야 하고(빠짐·추가 없음), 대사의 화자는 그 블록 참여 빵이어야 한다. */
    public static void validateBlocks(List<StoryWrittenBlockDto> written, List<BlockPlan> planned) {
        require(written != null, "blocks가 없습니다.");
        Map<String, StoryWrittenBlockDto> byId = written.stream()
                .collect(Collectors.toMap(StoryWrittenBlockDto::id, Function.identity(), (a, b) -> {
                    throw new AiStoryInvalidResponseException("중복된 block id: " + a.id());
                }));
        require(byId.keySet().equals(planned.stream().map(BlockPlan::id).collect(Collectors.toSet())),
                "요청한 블록 구성과 다릅니다: " + byId.keySet());
        for (BlockPlan plan : planned) {
            StoryWrittenBlockDto block = byId.get(plan.id());
            if (plan.type() == AiSceneBlockType.TRANSITION) {
                requireText(block.transitionText(), TRANSITION_MAX, "transitionText");
                continue;
            }
            if (plan.type() == AiSceneBlockType.MESSENGER) {
                requireText(block.roomName(), ROOM_NAME_MAX, "roomName");
            }
            require(block.messages() != null && !block.messages().isEmpty()
                    && block.messages().size() <= MESSAGES_PER_BLOCK_MAX, plan.id() + " 대사 수가 올바르지 않습니다.");
            for (StoryWrittenBlockDto.Message message : block.messages()) {
                require(message.speakerId() != null && plan.participantIds().contains(message.speakerId()),
                        plan.id() + " 블록 참여 빵이 아닌 화자: " + message.speakerId());
                requireText(message.text(), MESSAGE_MAX, "message text");
            }
        }
    }

    public static void validateSummary(SummarizeResponseDto response, Collection<UUID> relationIds) {
        require(response != null, "정리 응답이 없습니다.");
        requireText(response.episodeSummary(), NOTE_MAX, "episodeSummary");
        require(response.relations() != null, "relations가 없습니다.");
        for (SummarizeResponseDto.RelationUpdate update : response.relations()) {
            require(update.breadId() != null && relationIds.contains(update.breadId()),
                    "관계 기록에 없는 breadId: " + update.breadId());
            if (update.recentMemory() != null) {
                require(update.recentMemory().length() <= MEMORY_MAX, "recentMemory가 너무 깁니다.");
            }
        }
    }

    public static void validateNote(LetterResponseDto response, Collection<UUID> castIds) {
        require(response != null, "쪽지 응답이 없습니다.");
        requireText(response.body(), NOTE_MAX, "note body");
        require(response.finalPartnerId() == null || castIds.contains(response.finalPartnerId()),
                "출연진에 없는 쪽지 발신 빵: " + response.finalPartnerId());
    }

    public static void validateReport(ReportResponseDto response) {
        require(response != null, "리포트 응답이 없습니다.");
        requireText(response.tendency(), 150, "tendency");
        requireText(response.feedback(), 150, "feedback");
        require(response.strengths() != null && !response.strengths().isEmpty() && response.strengths().size() <= 3,
                "strengths는 1~3개여야 합니다.");
        require(response.weaknesses() != null && !response.weaknesses().isEmpty() && response.weaknesses().size() <= 3,
                "weaknesses는 1~3개여야 합니다.");
        require(response.highlights() != null && response.highlights().episode2() != null
                && response.highlights().episode4() != null, "highlights가 없습니다.");
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

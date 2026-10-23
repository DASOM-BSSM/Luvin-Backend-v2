package com.luvin.ai.client.story;

import com.luvin.ai.client.dto.LetterResponseDto;
import com.luvin.ai.client.dto.ReportHighlightResponseDto;
import com.luvin.ai.client.dto.ReportHighlightsResponseDto;
import com.luvin.ai.client.dto.ReportResponseDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FastAPI 계약이 확정되기 전에 장면 블록 회차 흐름을 끝까지 돌려보기 위한 stub. 요청받은 블록 구성을 그대로 따라
 * 정해진 문구로 대사를 만든다 — 같은 요청이면 항상 같은 응답이다. 운영 대사가 아니다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.story.client", havingValue = "stub", matchIfMissing = true)
public class AiStoryStubClient implements AiStoryClient {

    @Override
    public StageIntroResponseDto stageIntro(String ownerSubject, StageIntroRequestDto request) {
        String setting = request.context().setting();
        List<StageIntroResponseDto.CastIntro> intros = request.context().cast().stream()
                .map(c -> new StageIntroResponseDto.CastIntro(c.breadId(), stageRole(setting, c),
                        c.name() + "은(는) " + request.settingTitle() + "에서 함께 지내게 될 빵이에요."))
                .toList();
        return new StageIntroResponseDto(intros);
    }

    @Override
    public EpisodeWriteResponseDto writeEpisode(String ownerSubject, EpisodeWriteRequestDto request) {
        Map<UUID, String> names = names(request.context());
        List<StoryWrittenBlockDto> blocks = request.blocks().stream()
                .map(b -> write(b, names, request.settingScene()))
                .toList();
        EpisodeWriteResponseDto.Intro intro = new EpisodeWriteResponseDto.Intro(
                request.settingScene(), request.commonEvent() + " — " + request.settingScene() + "에서 생긴 일.");
        return new EpisodeWriteResponseDto(intro, blocks);
    }

    @Override
    public StoryBlocksResponseDto writeChoiceResult(String ownerSubject, ChoiceResultRequestDto request) {
        Map<UUID, String> names = names(request.context());
        String scene = request.accepted() ? "마음이 닿은 순간" : "엇갈린 순간";
        return new StoryBlocksResponseDto(request.blocks().stream().map(b -> write(b, names, scene)).toList());
    }

    @Override
    public StoryBlocksResponseDto writeInterview(String ownerSubject, InterviewRequestDto request) {
        Map<UUID, String> names = names(request.context());
        List<StoryWrittenBlockDto> blocks = request.blocks().stream().map(b -> {
            UUID interviewee = b.focusBreadId();
            String reasons = request.context().choices().stream()
                    .filter(c -> interviewee.equals(c.breadId()))
                    .map(StoryContextDto.ChoiceRecord::reasonCode)
                    .reduce((a, c) -> a + ", " + c)
                    .orElse("없음");
            return new StoryWrittenBlockDto(b.id(), null, null, List.of(
                    message(interviewee, names.get(interviewee) + "의 인터뷰: 이번 시즌을 돌아보면 기억나는 순간이 많아요."),
                    message(interviewee, "그때의 선택(" + reasons + ")은 지금 생각해도 제 마음 그대로였어요.")));
        }).toList();
        return new StoryBlocksResponseDto(blocks);
    }

    @Override
    public SummarizeResponseDto summarize(String ownerSubject, SummarizeRequestDto request) {
        Map<UUID, Integer> lineCounts = new HashMap<>();
        request.script().forEach(line -> lineCounts.merge(line.speakerId(), 1, Integer::sum));
        List<SummarizeResponseDto.RelationUpdate> updates = request.context().relations().stream().map(r -> {
            int lines = lineCounts.getOrDefault(r.breadId(), 0);
            List<String> facts = new ArrayList<>(r.knownFacts());
            facts.add(request.order() + "화에서 알게 된 점");
            List<String> threads = request.order() % 2 == 0
                    ? List.of() : List.of(request.order() + "화에서 다음에 같이 하자고 한 약속");
            return new SummarizeResponseDto.RelationUpdate(r.breadId(),
                    request.order() + "화까지 함께한 기억이 쌓였다.",
                    request.order() + "화: 함께 이야기를 나눴다",
                    facts, threads, lines > 2 ? "친해짐" : "어색함", lines > 2 ? 3 : -1);
        }).toList();
        return new SummarizeResponseDto(request.order() + "화 요약: 대사 " + request.script().size() + "줄.", updates);
    }

    @Override
    public LetterResponseDto writeEpisodeNote(String ownerSubject, EpisodeNoteRequestDto request) {
        UUID partner = closest(request.context());
        String body = request.seasonFinale()
                ? "시즌을 함께해줘서 고마워. 마지막 날까지 네 곁이라 좋았어."
                : request.order() + "화 이야기, 나는 오래 기억할 것 같아.";
        return new LetterResponseDto(UUID.randomUUID(), partner, body, "default");
    }

    @Override
    public ReportResponseDto writeSeasonReport(String ownerSubject, SeasonReportRequestDto request) {
        ReportHighlightResponseDto highlight = new ReportHighlightResponseDto(
                "같이 있어서 다행이야.", "관계가 가까워진 순간이에요.");
        return new ReportResponseDto(closest(request.context()),
                "관계 안에서 상대의 마음을 먼저 살피는 편이에요.",
                "가끔은 내 마음도 먼저 꺼내 보면 좋아요.",
                List.of("배려심"), List.of("망설임"),
                new ReportHighlightsResponseDto(highlight, highlight), List.of(), "default");
    }

    private StoryWrittenBlockDto write(StoryBlockPlanDto block, Map<UUID, String> names, String scene) {
        if ("transition".equals(block.type())) {
            return new StoryWrittenBlockDto(block.id(), "그날 저녁", null, null);
        }
        List<UUID> speakers = block.participantIds();
        int lines = block.shortReply() ? 2 : Math.max(3, speakers.size());
        boolean messenger = "messenger".equals(block.type());
        List<StoryWrittenBlockDto.Message> messages = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            UUID speaker = speakers.get(i % speakers.size());
            String text = names.get(speaker) + ": " + scene + " 이야기 " + (i + 1);
            messages.add(messenger
                    ? new StoryWrittenBlockDto.Message(speaker, text, i < lines - 1, i == lines - 1 ? Boolean.TRUE : null,
                            i == 0 ? Boolean.TRUE : null)
                    : message(speaker, text));
        }
        String roomName = messenger
                ? ("group".equals(block.messengerKind()) ? scene + " 단톡방" : names.get(block.focusBreadId()))
                : null;
        return new StoryWrittenBlockDto(block.id(), null, roomName, messages);
    }

    private static StoryWrittenBlockDto.Message message(UUID speaker, String text) {
        return new StoryWrittenBlockDto.Message(speaker, text, null, null, null);
    }

    private static Map<UUID, String> names(StoryContextDto context) {
        Map<UUID, String> names = new HashMap<>();
        context.cast().forEach(c -> names.put(c.breadId(), c.name()));
        return names;
    }

    private static UUID closest(StoryContextDto context) {
        return context.relations().stream()
                .max(Comparator.comparingInt(StoryContextDto.Relation::affinity))
                .map(StoryContextDto.Relation::breadId)
                .orElse(null);
    }

    private static String stageRole(String setting, StoryContextDto.CastMember member) {
        String place = switch (setting) {
            case "school" -> "같은 반";
            case "campus" -> "같은 과";
            case "office" -> "같은 팀";
            default -> "같은 매장";
        };
        return member.isMe() ? place + " 새 얼굴" : place + " " + member.name();
    }
}

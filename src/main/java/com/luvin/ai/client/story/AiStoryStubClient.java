package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.ChoiceCandidate;
import com.luvin.ai.client.story.StoryTypes.GeneratedBlock;
import com.luvin.ai.client.story.StoryTypes.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FastAPI 계약이 연결되기 전에 장면 블록 회차 흐름을 끝까지 돌려보기 위한 stub. 받은 설계(블록 id·참여 빵)를 그대로
 * 따라 정해진 문구로 대사를 만든다 — 같은 요청이면 항상 같은 응답이다. 운영 대사가 아니다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.story.client", havingValue = "stub", matchIfMissing = true)
public class AiStoryStubClient implements AiStoryClient {

    /** 회차마다 다른 임시 쪽지 문구(1~10화, 10화가 시즌 종료 쪽지). 실제 문구는 FastAPI가 쓴다. */
    private static final List<String> NOTES = List.of(
            "처음 만난 날인데 이상하게 편했어. 내일도 인사해줄 거지?",
            "단톡에서 네가 보낸 이모티콘, 나 혼자 계속 봤어.",
            "둘만 남았을 때 조금 떨렸는데, 너도 그랬을까.",
            "준비하느라 힘들었지? 네 덕분에 다 잘 끝났어.",
            "오늘 너한테 연락할까 말까 오래 고민했어.",
            "그날 밤 했던 이야기, 아직도 귓가에 맴돌아.",
            "요즘 바빠서 얼굴을 못 봤네. 밥은 잘 챙겨 먹어.",
            "그때 내가 한 말이 서운했다면 미안해. 다시 얘기하고 싶어.",
            "다시 웃으면서 이야기할 수 있어서 다행이야.",
            "시즌을 함께해줘서 고마워. 마지막 날까지 네 곁이라 좋았어.");

    /** 배경별·회차 사건에 맞춘 임시 전환 카드 문구(1~10화). 실제 문구는 FastAPI가 쓴다. */
    private static final Map<String, List<String>> TRANSITIONS = Map.of(
            "school", List.of("새 교실 문 앞", "하교 후 울린 단톡 알림", "다들 먼저 가고 난 교실", "축제 준비로 남은 방과 후",
                    "조퇴하고 돌아온 오후", "축제가 끝난 운동장", "독서실 앞 자판기", "회의가 끝난 빈 교실",
                    "체육대회가 끝난 저녁", "종업식 날 교문 앞"),
            "campus", List.of("환영회가 끝난 가게 앞", "과제방 첫 알림", "불 꺼진 과방", "MT 장보기가 끝난 마트 앞",
                    "시험 전날 도서관", "MT 숙소의 밤", "과제에 묻힌 새벽", "조별 회의가 끝난 강의실",
                    "종강 모임 가는 길", "마지막 수업이 끝난 캠퍼스"),
            "office", List.of("첫 회식이 끝난 밤", "퇴근길에 울린 단톡 알림", "야근 후 엘리베이터", "워크숍 준비 회의실",
                    "실수한 날 퇴근길", "워크숍 숙소의 저녁", "마감에 쫓기는 사무실", "회의가 끝난 탕비실",
                    "프로젝트가 끝난 날 저녁", "연말 회식 뒤 거리"),
            "cafe", List.of("첫 근무가 끝난 매장", "근무 단톡 첫 알림", "마감 정리하는 매장", "행사 준비로 늦게 남은 날",
                    "진상 손님이 떠난 뒤", "행사가 끝난 주말 밤", "엇갈린 근무표 앞", "대타 문제로 어색한 휴게실",
                    "마감 후 야식집", "마지막 근무 날 퇴근길"));

    @Override
    public StageIntroResponse stageIntro(String ownerSubject, StageIntroRequest request) {
        String place = switch (request.setting()) {
            case "school" -> "같은 반";
            case "campus" -> "같은 과";
            case "office" -> "같은 팀";
            default -> "같은 매장";
        };
        return new StageIntroResponse(request.cast().stream()
                .map(c -> new StageIntroResponse.Cast(c.breadId(), place + " " + roleWord(c.breadId()),
                        "me".equals(c.breadId()) ? null : c.name() + "와 함께 지내요"))
                .map(c -> new StageIntroResponse.Cast(c.breadId(), cut(c.role(), 10), c.description() == null
                        ? null : cut(c.description(), 25)))
                .toList());
    }

    @Override
    public EpisodeResponse writeEpisode(String ownerSubject, EpisodeRequest request) {
        Map<String, String> names = names(request.context());
        List<GeneratedBlock> blocks = request.design().stream().map(design -> switch (design.type()) {
            case "transition" -> new GeneratedBlock(design.id(), null,
                    TRANSITIONS.get(request.context().setting()).get(request.order() - 1), null);
            case "choice" -> new GeneratedBlock(design.id(), null, null, design.candidateIds().stream()
                    .map(id -> new ChoiceCandidate(id, cut(request.order() + "화에 함께한 순간", 20)))
                    .toList());
            default -> new GeneratedBlock(design.id(),
                    lines(design.participantIds(), names, request.settingScene(), "messenger".equals(design.type()),
                            "alone".equals(design.pairTarget()), false),
                    null, null);
        }).toList();
        return new EpisodeResponse(new StoryTypes.Intro(cut(request.settingScene(), 8),
                cut(request.settingScene(), 20),
                request.commonEvent() + " — " + request.settingScene() + "에서 생긴 일."), blocks,
                request.threads().stream().map(StoryTypes.Thread::id).toList());
    }

    @Override
    public ChoiceResultResponse writeChoiceResult(String ownerSubject, ChoiceResultRequest request) {
        Map<String, String> names = names(request.context());
        List<Message> reaction = lines(List.of("me", request.breadId()), names,
                request.accepted() ? "마음이 닿은 순간" : "엇갈린 순간", false, false, request.shortReply());
        List<ChoiceResultResponse.FallbackBlock> fallbacks = new ArrayList<>();
        if (!request.accepted()) {
            boolean alone = request.pointId() == 2;
            fallbacks.add(new ChoiceResultResponse.FallbackBlock("f1", alone ? "alone" : "friend",
                    lines(alone ? List.of("me") : List.of("me", "friend"), names, "그 뒤", false, alone, false)));
        }
        return new ChoiceResultResponse(reaction, fallbacks);
    }

    @Override
    public InterviewResponse writeInterview(String ownerSubject, InterviewRequest request) {
        Map<String, String> names = names(request.context());
        return new InterviewResponse(request.items().stream().map(item -> {
            String reasons = item.choiceHistory().stream()
                    .map(h -> h.reason() == null ? (h.accepted() ? "accepted" : "alone") : h.reason())
                    .reduce((a, b) -> a + ", " + b).orElse("없음");
            return new InterviewResponse.Interview(item.breadId(), List.of(
                    chat("i1", item.breadId(), names.get(item.breadId()) + ": 이번 시즌을 돌아보면 기억나는 순간이 많아요."),
                    chat("i2", item.breadId(), "그때의 선택(" + reasons + ")은 지금 생각해도 제 마음 그대로였어요.")));
        }).toList());
    }

    @Override
    public SummarizeResponse summarize(String ownerSubject, SummarizeRequest request) {
        Map<String, Integer> lineCounts = new HashMap<>();
        request.blocks().forEach(b -> {
            if (b.messages() != null) {
                b.messages().forEach(m -> lineCounts.merge(m.speakerId(), 1, Integer::sum));
            }
        });
        List<SummarizeResponse.RelationUpdate> updates = request.relations().stream().map(r -> {
            int lines = lineCounts.getOrDefault(r.breadId(), 0);
            List<StoryTypes.Thread> added = request.order() % 2 == 0 ? List.of()
                    : List.of(new StoryTypes.Thread("t" + request.order(), request.order() + "화에서 한 약속"));
            List<String> resolved = request.order() % 2 == 0
                    ? List.of("t" + (request.order() - 1)) : List.of();
            return new SummarizeResponse.RelationUpdate(r.breadId(), request.order() + "화까지 함께한 기억이 쌓였다.",
                    request.order() + "화: 함께 이야기를 나눴다", List.of(request.order() + "화에서 알게 된 점"), added,
                    resolved, lines > 2 ? "친해짐" : "어색함", lines > 2 ? 3 : -1, "대화량 기준",
                    cut(request.order() + "화에 이야기를 나눔", 20), lines > 2 ? "casual" : null);
        }).toList();
        return new SummarizeResponse(request.order() + "화 요약: 장면 " + request.blocks().size() + "개.", updates);
    }

    @Override
    public EpisodeNoteResponse writeEpisodeNote(String ownerSubject, EpisodeNoteRequest request) {
        return new EpisodeNoteResponse(null, request.sender().breadId(), NOTES.get(request.order() - 1), "default");
    }

    @Override
    public SeasonReportResponse writeSeasonReport(String ownerSubject, SeasonReportRequest request) {
        SeasonReportResponse.Highlight highlight = new SeasonReportResponse.Highlight(
                "같이 있어서 다행이야.", "관계가 가까워진 순간이에요.");
        return new SeasonReportResponse(request.romancePartnerId(),
                "관계 안에서 상대의 마음을 먼저 살피는 편이에요.",
                "가끔은 내 마음도 먼저 꺼내 보면 좋아요.",
                List.of("배려심"), List.of("망설임"),
                new SeasonReportResponse.Highlights(highlight, highlight), "default");
    }

    /** 첫 줄은 내레이션, 혼자인 장면은 독백, messenger면 읽음·입력 중·지운 메시지 연출을 하나씩 넣는다. */
    private static List<Message> lines(List<String> speakers, Map<String, String> names, String scene,
                                       boolean messenger, boolean alone, boolean shortReply) {
        List<Message> messages = new ArrayList<>();
        if (!messenger) {
            messages.add(new Message("n1", StoryTypes.NARRATOR, scene, StoryTypes.KIND_NARRATION, null, null, null, null));
        }
        int count = shortReply ? 2 : Math.max(3, speakers.size());
        for (int i = 0; i < count; i++) {
            String speaker = speakers.get(i % speakers.size());
            String text = names.get(speaker) + ": " + scene + " 이야기 " + (i + 1);
            String kind = alone ? StoryTypes.KIND_MONOLOGUE : StoryTypes.KIND_CHAT;
            messages.add(messenger
                    ? new Message("m" + i, speaker, text, kind, i < count - 1, i == count - 1 ? Boolean.TRUE : null,
                            i == 0 ? Boolean.TRUE : null, "오후 " + (4 + i / 2) + "시 " + (10 + i * 7 % 50) + "분")
                    : new Message("m" + i, speaker, text, kind, null, null, null, null));
        }
        return messages;
    }

    private static Message chat(String id, String speaker, String text) {
        return new Message(id, speaker, text, StoryTypes.KIND_CHAT, null, null, null, null);
    }

    private static Map<String, String> names(StoryTypes.GenerationContext context) {
        Map<String, String> names = new HashMap<>();
        context.cast().forEach(c -> names.put(c.breadId(), c.name()));
        return names;
    }

    private static String roleWord(String breadId) {
        return switch (breadId) {
            case "me" -> "새 얼굴";
            case "friend" -> "단짝";
            case "maker" -> "분위기 담당";
            default -> "친구";
        };
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}

package com.luvin.ai.service.story;

import com.fasterxml.jackson.databind.JsonNode;
import com.luvin.ai.client.dto.TraitsDto;
import com.luvin.ai.client.story.SeasonDesignRequest;
import com.luvin.ai.client.story.SeasonDesignResponse;
import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.config.AiStoryConfig;
import com.luvin.ai.domain.AiCastRole;
import com.luvin.ai.domain.AiSeasonSetting;
import com.luvin.ai.domain.AiStoryRoute;
import com.luvin.ai.dto.AiReportView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSeasonDesignsTest {

    private static final List<String> BREADS = List.of("소금빵", "단팥빵", "베이글", "크루아상", "모닝빵", "식빵");

    @Test
    void 직전_시즌부터_최근_순으로_최대_3개를_보내고_끊기면_멈춘다() {
        List<AiPastSeason> past = List.of(solo(4), solo(3), romance(2), solo(1));
        SeasonDesignRequest request = request(5, past).orElseThrow();

        assertEquals(List.of(4, 3, 2), request.previousSeasons().stream()
                .map(SeasonDesignRequest.PreviousSeason::seasonNumber).toList());
        assertEquals("male", request.gender());
        assertEquals("office", request.setting());
        // 이전 출연진 이름은 기록을 보내지 않는 시즌까지 전부 보낸다.
        assertTrue(request.previousCastNames().containsAll(List.of("하림4", "하림1")));
        assertEquals(List.of("부제 4", "부제 3", "부제 2", "부제 1"), request.previousStageSubtitles());

        // 리포트가 없는 시즌에서 끊긴다(3시즌 기록 없음 → 4시즌만).
        List<AiPastSeason> broken = List.of(solo(4), withoutReport(solo(3)), solo(2));
        assertEquals(1, request(5, broken).orElseThrow().previousSeasons().size());
    }

    @Test
    void 직전_시즌에_비교할_기록이_없으면_설정을_만들지_않는다() {
        assertTrue(request(3, List.of(withoutReport(solo(2)), solo(1))).isEmpty());
        // 직전 시즌(2)이 아니라 1시즌만 있으면 순서가 맞지 않는다.
        assertTrue(request(3, List.of(solo(1))).isEmpty());
        // 빵 종류가 5개보다 적으면 NPC 5명이 겹치지 않을 수 없다.
        assertTrue(AiSeasonDesigns.request(2, "MALE", 26, traits(50, 50), AiSeasonSetting.OFFICE, List.of(solo(1)),
                BREADS.subList(0, 4)).isEmpty());
    }

    @Test
    void 연애_흐름이면_최종_상대를_혼자_흐름이면_null을_키와_함께_보낸다() throws Exception {
        SeasonDesignRequest request = request(3, List.of(romance(2), solo(1))).orElseThrow();
        SeasonDesignRequest.PreviousSeason romance = request.previousSeasons().get(0);
        assertEquals("romance", romance.route());
        assertEquals(new SeasonDesignRequest.PreviousPartner("하림2", "host", "늘 분위기를 띄운다"),
                romance.finalPartner());
        assertEquals(List.of(new SeasonDesignRequest.PreviousChoice(1, 3, "하림2에게 함께하자고 했고 상대가 받아들였다."),
                new SeasonDesignRequest.PreviousChoice(3, 6, "하림2에게 마음을 전했지만 상대가 한발 물러서며 거절했다."),
                new SeasonDesignRequest.PreviousChoice(4, 8, "혼자 있기를 골랐다.")), romance.choices());
        // 피드백은 한 줄로 바꾸고 겹치면 하나만, 최대 5개.
        assertEquals(List.of("조금 더 솔직하게 말해줬으면"), romance.userFeedbacks());

        JsonNode json = AiStoryConfig.STORY_OBJECT_MAPPER.valueToTree(request);
        assertTrue(json.get("previousSeasons").get(1).has("finalPartner"));
        assertTrue(json.get("previousSeasons").get(1).get("finalPartner").isNull());
        assertEquals(13, json.get("traits").size());
    }

    @Test
    void 응답이_계약과_다르면_다시_생성한다() {
        SeasonDesignRequest request = request(2, List.of(solo(1))).orElseThrow();
        AiSeasonDesigns.validate(design(List.of("하림", "수빈", "도윤", "서진", "지안"), BREADS), request);

        assertThrows(AiStoryInvalidResponseException.class, () -> AiSeasonDesigns.validate(
                design(List.of("하림", "수빈", "도윤", "서진", "지안"),
                        List.of("소금빵", "소금빵", "베이글", "크루아상", "모닝빵")), request));
        assertThrows(AiStoryInvalidResponseException.class, () -> AiSeasonDesigns.validate(
                design(List.of("하림1", "수빈", "도윤", "서진", "지안"), BREADS), request));
        // 이전 시즌 출연진 이름을 다시 쓰면 안 된다.
        SeasonDesignRequest reused = request(2, List.of(solo(1), named(0, "하림"))).orElseThrow();
        assertThrows(AiStoryInvalidResponseException.class, () -> AiSeasonDesigns.validate(
                design(List.of("하림", "수빈", "도윤", "서진", "지안"), BREADS), reused));
        // 허용 목록 밖의 빵 종류.
        assertThrows(AiStoryInvalidResponseException.class, () -> AiSeasonDesigns.validate(
                design(List.of("하림", "수빈", "도윤", "서진", "지안"),
                        List.of("바게트", "단팥빵", "베이글", "크루아상", "모닝빵")), request));
    }

    @Test
    void 후보의_애착_유형은_설정의_회피도와_불안도로_정한다() {
        SeasonDesignResponse design = design(List.of("하림", "수빈", "도윤", "서진", "지안"), BREADS);
        Map<AiCastRole, SeasonDesignResponse.Cast> assigned = AiSeasonDesigns.assign(design);

        assertEquals("도윤", assigned.get(AiCastRole.AVOIDANT).name());
        assertEquals("하림", assigned.get(AiCastRole.ANXIOUS).name());
        assertEquals("수빈", assigned.get(AiCastRole.SECURE).name());
        assertEquals("서진", assigned.get(AiCastRole.FRIEND).name());
        assertEquals("지안", assigned.get(AiCastRole.MOOD_MAKER).name());
    }

    @Test
    void 저장한_설정으로_회차_본문과_리포트의_seasonContext를_만든다() {
        List<AiPastSeason> past = List.of(solo(1));
        SeasonDesignRequest request = request(2, past).orElseThrow();
        AiSeasonDesignRecord record = AiSeasonDesigns.record(request,
                design(List.of("하림", "수빈", "도윤", "서진", "지안"), BREADS), past);

        StoryTypes.EpisodeSeasonContext third = AiSeasonDesigns.episodeContext(2, record, 3);
        assertEquals("먼저 말하기 어려움", third.testsWeakness());
        assertEquals(List.of("오늘 같이 가자"), third.likedLines());
        assertEquals(request.traits(), third.traits());
        assertEquals(null, AiSeasonDesigns.episodeContext(2, record, 2).testsWeakness());

        StoryTypes.ReportSeasonContext report = AiSeasonDesigns.reportContext(2, record);
        assertEquals(List.of("먼저 말하기 어려움"), report.previousWeaknesses());
        assertEquals(List.of("상대 말 경청"), report.previousStrengths());

        String plot = AiSeasonDesigns.referencePlot("기존 줄거리", record, 3);
        assertTrue(plot.startsWith("[이번 시즌 3화 초안]\n제목: 3화 제목"), plot);
        assertTrue(plot.endsWith("기존 줄거리"), plot);
    }

    // ---------------------------------------------------------------- fixtures

    private static Optional<SeasonDesignRequest> request(int seasonNumber, List<AiPastSeason> past) {
        return AiSeasonDesigns.request(seasonNumber, "MALE", 26, traits(50, 50), AiSeasonSetting.OFFICE, past, BREADS);
    }

    private static AiPastSeason solo(int number) {
        return new AiPastSeason(number, AiSeasonSetting.OFFICE, AiStoryRoute.SOLO, null, List.of(), report(),
                List.of("조금 더 솔직하게\n말해줬으면"), List.of("오늘 같이 가자", "오늘 같이 가자"),
                List.of("하림" + number, "수빈" + number), "부제 " + number);
    }

    private static AiPastSeason romance(int number) {
        AiPastSeason base = solo(number);
        return new AiPastSeason(number, AiSeasonSetting.OFFICE, AiStoryRoute.ROMANCE,
                new AiPastSeason.Partner("하림" + number, AiCastRole.MOOD_MAKER, "늘 분위기를 띄운다"),
                List.of(new AiPastSeason.Choice(1, 3, "하림" + number, AiCastRole.MOOD_MAKER, true, "affinity_met"),
                        new AiPastSeason.Choice(3, 6, "하림" + number, AiCastRole.MOOD_MAKER, false,
                                "avoidant_retreat"),
                        new AiPastSeason.Choice(4, 8, null, null, false, "alone")),
                base.report(), List.of("조금 더 솔직하게 말해줬으면", "조금 더 솔직하게 말해줬으면"), base.likedLines(),
                base.castNames(), base.stageSubtitle());
    }

    private static AiPastSeason named(int number, String name) {
        AiPastSeason base = solo(number);
        return new AiPastSeason(number, base.setting(), base.route(), null, List.of(), base.report(), List.of(),
                List.of(), List.of(name), null);
    }

    private static AiPastSeason withoutReport(AiPastSeason season) {
        return new AiPastSeason(season.seasonNumber(), season.setting(), season.route(), season.partner(),
                season.choices(), null, season.feedbacks(), season.likedLines(), season.castNames(),
                season.stageSubtitle());
    }

    private static AiReportView report() {
        return new AiReportView(null, "마음을 천천히 표현했다.", "편안했던 순간을 돌아보세요.", List.of("상대 말 경청"),
                List.of("먼저 말하기 어려움"), null, null);
    }

    private static SeasonDesignResponse design(List<String> names, List<String> breads) {
        List<String> roles = List.of("candidate", "candidate", "candidate", "friend", "host");
        // 셋째 후보가 회피도, 첫째 후보가 불안도가 가장 높다.
        List<TraitsDto> scores = List.of(traits(40, 90), traits(40, 40), traits(85, 40), traits(99, 99),
                traits(99, 99));
        List<SeasonDesignResponse.Cast> cast = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            cast.add(new SeasonDesignResponse.Cast(roles.get(i), names.get(i), breads.get(i), "같은 팀 동기",
                    "말을 끝까지 들어준다", scores.get(i), "의도"));
        }
        List<SeasonDesignResponse.Episode> episodes = new ArrayList<>();
        for (int order = 1; order <= 10; order++) {
            episodes.add(new SeasonDesignResponse.Episode(order, order + "화 제목", "첫 줄\n둘째 줄", "저녁, 회사 앞",
                    AiStoryCatalog.pointOf(order), order == 3 ? "먼저 말하기 어려움" : null));
        }
        return new SeasonDesignResponse(new SeasonDesignResponse.Stage("In Office", "새 부제"), cast, episodes);
    }

    private static TraitsDto traits(int avoidance, int anxiety) {
        BigDecimal mid = BigDecimal.valueOf(50);
        return new TraitsDto(mid, BigDecimal.valueOf(anxiety), BigDecimal.valueOf(avoidance), mid, mid, mid, mid, mid,
                mid, mid, mid, mid, mid);
    }
}

package com.luvin.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luvin.LuvinBackendV2Application;
import com.luvin.ai.domain.AiCharacterRole;
import com.luvin.ai.domain.AiSeason;
import com.luvin.ai.domain.AiSeasonCharacter;
import com.luvin.ai.domain.AiEpisodeProgress;
import com.luvin.ai.repository.AiEpisodeProgressRepository;
import com.luvin.ai.repository.AiSeasonCharacterRepository;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.common.security.AuthenticatedUser;
import com.luvin.common.security.JwtTokenProvider;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 장면 블록 회차(1~10화)를 실제 서버 + DB + stub FastAPI(app.ai.story.client=stub)로 처음부터 끝까지 돌린다.
 * AI 시즌(seasonId, representative + candidate 3명)은 DB에 직접 만들고, 다음 시즌 생성(POST /v1/seasons)만 같은 JVM의
 * 가짜 AI 서버가 받는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = LuvinBackendV2Application.class)
@TestPropertySource(properties = {
        "app.ai.story.client=stub",
        "app.ai.story.token-charge-enabled=false",
        "app.dev-tools.enabled=true"
})
class AiStoryFlowIntegrationTest {

    private static final List<String> SEASON_TABLES = List.of(
            "ai_season_choices", "ai_bread_relations", "ai_episode_contents", "ai_season_story_reports",
            "partner_letters", "ai_story_feedbacks", "ai_story_message_likes", "ai_episode_unlocks",
            "ai_season_characters",
            "ai_episode_progresses");

    private static final HttpServer FAKE_AI = startFakeAi();

    @DynamicPropertySource
    static void aiBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("app.ai.base-url", () -> "http://localhost:" + FAKE_AI.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeAi() {
        FAKE_AI.stop(0);
    }

    @LocalServerPort
    private int port;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AiSeasonRepository seasonRepository;
    @Autowired
    private AiSeasonCharacterRepository characterRepository;
    @Autowired
    private AiEpisodeProgressRepository progressRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> seasonIds = new ArrayList<>();

    private record TestUser(Long id, String token) {
    }

    private record Season(Long pk, UUID representative, List<UUID> candidates) {
    }

    @AfterEach
    void tearDown() {
        for (Long seasonPk : seasonIds) {
            SEASON_TABLES.forEach(t -> jdbcTemplate.update("DELETE FROM " + t + " WHERE season_id = ?", seasonPk));
            jdbcTemplate.update("DELETE FROM ai_seasons WHERE id = ?", seasonPk);
        }
        userRepository.deleteAllById(userIds);
    }

    @Test
    void 시즌_전체_1화부터_10화까지_연애_흐름() throws Exception {
        TestUser user = createUser(22);
        Season season = createSeason(user);

        // 1화: 무대 소개 + 상황 소개 + 블록 계약 형식
        JsonNode ep1 = readyContent(user, 1);
        JsonNode stageIntro = ep1.get("stageIntro");
        assertTrue(Set.of("campus", "cafe").contains(stageIntro.get("setting").asText()), stageIntro.toString());
        assertTrue(Set.of("In Campus", "In Cafe").contains(stageIntro.get("title").asText()), stageIntro.toString());
        assertEquals(6, stageIntro.get("cast").size());
        assertFalse(ep1.get("intro").get("title").asText().isBlank());
        assertEquals(List.of("group", "transition", "pair", "messenger"), blockTypes(ep1));
        // 회차 시작 화면 이름, 무대 소개 표기(In …)와 내 빵 역할 "나", 개인톡 메시지의 이야기 속 시각
        assertFalse(ep1.get("intro").get("episodeName").asText().isBlank());
        assertTrue(ep1.get("blocks").get(3).get("messages").get(0).has("sentAtLabel"));
        String ep1Transition = ep1.get("blocks").get(1).get("transitionText").asText();
        // 출연진 이름: 내 빵은 닉네임, 나머지는 "형용사 빵종류"이고 이름·빵 종류가 겹치지 않는다.
        Set<String> names = new HashSet<>();
        Set<String> breadTypes = new HashSet<>();
        for (JsonNode cast : stageIntro.get("cast")) {
            names.add(cast.get("name").asText());
            breadTypes.add(cast.get("breadType").asText());
            if (cast.get("breadId").asText().equals(season.representative().toString())) {
                assertEquals("빵", cast.get("name").asText());
                assertEquals("나", cast.get("role").asText());
                assertTrue(cast.get("description").isNull());
            } else {
                assertTrue(cast.get("name").asText().endsWith(" " + cast.get("breadType").asText()), cast.toString());
            }
        }
        assertEquals(6, names.size());
        assertEquals(6, breadTypes.size());
        // 내레이션은 speakerId 없이 kind narration
        JsonNode firstLine = ep1.get("blocks").get(0).get("messages").get(0);
        assertEquals("narration", firstLine.get("kind").asText());
        assertTrue(firstLine.get("speakerId").isNull());
        assertFalse(ep1.get("blocks").get(0).get("messages").get(0).has("read"));

        // 1화를 완료하기 전에는 2화를 만들지 않는다.
        assertEquals("PREVIOUS_EPISODE_NOT_COMPLETED", error(get(user, "/episodes/2/content")).get("code").asText());
        complete(user, 1);

        // 전환 카드 문구는 회차마다 다르다. 2화는 transition 앞에 단톡방(group 메신저)이 있다.
        JsonNode ep2 = readyContent(user, 2);
        assertEquals(List.of("group", "messenger", "transition", "pair"), blockTypes(ep2));
        assertFalse(ep1Transition.equals(ep2.get("blocks").get(2).get("transitionText").asText()));
        JsonNode groupChat = ep2.get("blocks").get(1);
        assertEquals("group", groupChat.get("messenger").get("kind").asText());
        assertTrue(groupChat.get("messages").get(0).has("read"));
        complete(user, 2);

        // 3화: 선택 전에는 완료할 수 없고, choice 후보는 연애 후보 3명이다.
        JsonNode ep3 = readyContent(user, 3);
        JsonNode choice = ep3.get("blocks").get(ep3.get("blocks").size() - 1);
        assertEquals("choice", choice.get("type").asText());
        assertEquals(1, choice.get("choice").get("pointId").asInt());
        Set<UUID> candidateIds = new HashSet<>();
        choice.get("choice").get("candidates").forEach(c -> candidateIds.add(UUID.fromString(c.get("breadId").asText())));
        assertEquals(new HashSet<>(season.candidates()), candidateIds);
        // 기억 한 줄은 회차 생성 응답(5-2 choice 블록의 candidates)에서 온다.
        choice.get("choice").get("candidates")
                .forEach(c -> assertEquals("3화에 함께한 순간", c.get("memory").asText()));
        // 정리 단계의 선택용 기억(choiceMemory)도 관계 기록에 저장된다 — 생성 응답에 기억이 없을 때 쓴다.
        assertEquals(5, jdbcTemplate.queryForObject("SELECT count(*) FROM ai_bread_relations WHERE season_id = ? "
                + "AND choice_memory = '2화에 이야기를 나눔'", Integer.class, season.pk()));
        assertEquals("CHOICE_REQUIRED", error(post(user, "/episodes/3/complete", null)).get("code").asText());

        UUID secure = candidateOf(user, "secure");
        JsonNode outcome = data(post(user, "/episodes/3/choices", "{\"pointId\":1,\"breadId\":\"" + secure + "\"}"));
        assertEquals("choice_result", outcome.get("blocks").get(0).get("type").asText());
        assertTrue(outcome.get("blocks").get(0).get("choiceResult").get("accepted").asBoolean());
        // 같은 선택을 다시 보내면 그대로, 다른 빵이면 409.
        assertEquals(200, post(user, "/episodes/3/choices", "{\"pointId\":1,\"breadId\":\"" + secure + "\"}").statusCode());
        assertEquals("CHOICE_ALREADY_MADE", error(post(user, "/episodes/3/choices",
                "{\"pointId\":1,\"breadId\":null}")).get("code").asText());
        complete(user, 3);
        // 다시 볼 때도 선택 결과 블록이 choice 블록 바로 뒤에 들어 있다.
        List<String> rewatch3 = blockTypes(data(get(user, "/episodes/3/content")));
        assertEquals("choice_result", rewatch3.get(rewatch3.indexOf("choice") + 1));
        assertEquals(blockTypes(ep3).size() + 1, rewatch3.size());

        // 4화 pair 상대 = ①에서 수락한 빵
        JsonNode ep4 = readyContent(user, 4);
        assertEquals(secure.toString(), pairFocus(ep4, user));
        complete(user, 4);

        readyContent(user, 5);
        post(user, "/episodes/5/choices", "{\"pointId\":2,\"breadId\":\"" + secure + "\"}");
        complete(user, 5);

        // ③ 수락이 되도록 호감을 올려 둔 뒤 마음을 전한다 → romance
        readyContent(user, 6);
        jdbcTemplate.update("UPDATE ai_bread_relations SET affinity = 70 WHERE season_id = ? AND target_bread_id = ?",
                season.pk(), secure);
        JsonNode confession = data(post(user, "/episodes/6/choices", "{\"pointId\":3,\"breadId\":\"" + secure + "\"}"));
        assertEquals("romance", confession.get("season").get("route").asText());
        assertEquals(secure.toString(), confession.get("season").get("romancePartnerId").asText());
        complete(user, 6);

        JsonNode ep7 = readyContent(user, 7);
        assertEquals(secure.toString(), pairFocus(ep7, user));
        complete(user, 7);

        readyContent(user, 8);
        post(user, "/episodes/8/choices", "{\"pointId\":4,\"breadId\":null}");
        complete(user, 8);

        readyContent(user, 9);
        complete(user, 9);

        // 10화: 인터뷰룸 5개, 선택 없음
        JsonNode ep10 = readyContent(user, 10);
        List<String> types10 = blockTypes(ep10);
        assertEquals(5, types10.stream().filter("interview"::equals).count());
        assertFalse(types10.contains("choice"));
        JsonNode state = complete(user, 10);
        assertEquals(10, state.get("currentOrder").asInt());

        // 최종 리포트는 기존 경로·형식 그대로, 시즌은 완료
        JsonNode report = data(get(user, "/report"));
        assertFalse(report.get("tendency").asText().isBlank());
        assertNotNull(report.get("highlights").get("episode2"));
        // 연애 흐름의 최종 상대는 연인
        assertEquals(secure.toString(), report.get("finalPartnerId").asText());
        assertEquals("COMPLETED", seasonRepository.findById(season.pk()).orElseThrow().getStatus().name());

        // 회차마다 쪽지가 하나씩 생겼다(10화 쪽지가 시즌 종료 쪽지).
        Integer letters = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM partner_letters WHERE season_id = ? AND episode_order IS NOT NULL",
                Integer.class, season.pk());
        assertEquals(10, letters);
        // 10화 쪽지(연애 흐름)는 연인이 보내고, 발신 빵 이름이 채워진다.
        JsonNode unread = data(get(user, "/letters/unread"));
        assertTrue(unread.get("message").asText().contains("시즌"), unread.toString());
        assertEquals(secure.toString(), unread.get("partnerId").asText());
        assertFalse(unread.get("partnerName").asText().isBlank());
        // 회차 쪽지는 그 회차 1:1 상대가 보내고(4화 = ①의 빵), 문구는 회차마다 다르다.
        assertEquals(secure, jdbcTemplate.queryForObject(
                "SELECT partner_id FROM partner_letters WHERE season_id = ? AND episode_order = 4", UUID.class, season.pk()));
        assertEquals(10, jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT message) FROM partner_letters WHERE season_id = ? AND episode_order IS NOT NULL",
                Integer.class, season.pk()));
    }

    @Test
    void 설문으로_만든_시즌은_바로_시작되고_10화_뒤에는_다음_시즌이_없다() throws Exception {
        TestUser user = createUser(26);
        String surveyResultId = submitSurvey(user);

        // 시즌을 만들 때 나이로 배경을 정하고 출연진(이름·성별·후보 순서)을 배정한다.
        JsonNode created = data(post(user, "/from-survey", "{\"surveyResultId\":\"" + surveyResultId + "\"}"));
        seasonIds.add(seasonRepository.findByMemberIdAndSeasonId(user.id(),
                UUID.fromString(created.get("seasonId").asText())).orElseThrow().getId());
        assertEquals("office", created.get("story").get("setting").asText());
        assertEquals(List.of(1, 2, 3), intList(created.get("story").get("unlockedOrders")));
        for (JsonNode character : created.get("characters")) {
            String castRole = character.get("castRole").asText();
            String expected = castRole.equals("my_bread") || castRole.equals("friend") ? "FEMALE" : "MALE";
            assertEquals(expected, character.get("gender").asText(), character.toString());
        }

        for (int order = 1; order <= 10; order++) {
            readyContent(user, order);
            Integer point = switch (order) {
                case 3 -> 1;
                case 5 -> 2;
                case 6 -> 3;
                case 8 -> 4;
                default -> null;
            };
            if (point != null) {
                post(user, "/episodes/" + order + "/choices", "{\"pointId\":" + point + ",\"breadId\":null}");
            }
            complete(user, order);
        }
        assertFalse(data(get(user, "/report")).get("feedback").asText().isBlank());
        // 끝난 시즌의 회차는 계속 다시 볼 수 있고, 다음 시즌은 만들지 않는다.
        assertEquals("ready", data(get(user, "/episodes/10/content")).get("status").asText());
        assertEquals("SEASON_COMPLETED", error(post(user, "/next", "{}")).get("code").asText());
    }

    @Test
    void 나이가_없으면_시즌을_만들_때_AGE_REQUIRED() throws Exception {
        TestUser user = createUser(null);
        String surveyResultId = submitSurvey(user);

        assertEquals("AGE_REQUIRED", error(post(user, "/from-survey",
                "{\"surveyResultId\":\"" + surveyResultId + "\"}")).get("code").asText());
        assertEquals(0, seasonRepository.findAllByMemberIdOrderBySeasonNumberAsc(user.id()).size());
    }

    @Test
    void 시즌_상태에_회차_구성과_완료한_회차가_담긴다() throws Exception {
        TestUser user = createUser(21);
        createSeason(user);

        JsonNode fresh = data(get(user, "/me"));
        assertEquals("blocks", fresh.get("episodeFormat").asText());
        assertTrue(fresh.get("story").isNull());

        readyContent(user, 1);
        // 1화 무대 소개 뒤에는 시즌 상태 characters[]에도 소개 한 줄이 있다(내 빵은 null).
        for (JsonNode character : data(get(user, "/me")).get("characters")) {
            if ("my_bread".equals(character.get("castRole").asText())) {
                assertTrue(character.get("description").isNull());
            } else {
                assertFalse(character.get("description").asText().isBlank(), character.toString());
            }
        }
        complete(user, 1);
        readyContent(user, 2);
        JsonNode story = data(get(user, "/me")).get("story");
        assertEquals(2, story.get("currentOrder").asInt());
        assertEquals(List.of(1), intList(story.get("completedOrders")));
        assertEquals(List.of(1, 2, 3), intList(story.get("unlockedOrders")));
        assertEquals(1, story.get("episodeTokenCost").asInt());
        assertEquals("blocks", data(get(user, "")).get(0).get("episodeFormat").asText());
    }

    @Test
    void 회차_구성은_항상_blocks다() throws Exception {
        TestUser user = createUser(21);
        Season season = createSeason(user);
        progressRepository.save(new AiEpisodeProgress(
                seasonRepository.findById(season.pk()).orElseThrow(), 1, UUID.randomUUID()));

        assertEquals("blocks", data(get(user, "/me")).get("episodeFormat").asText());
        assertEquals("ready", readyContent(user, 1).get("status").asText());
    }

    @Test
    void 성별이_없어도_AGE_REQUIRED이고_성별은_MALE_FEMALE만_받는다() throws Exception {
        TestUser user = createUser(25);
        jdbcTemplate.update("UPDATE users SET gender = NULL WHERE user_id = ?", user.id());
        createSeason(user);

        assertEquals("AGE_REQUIRED", error(get(user, "/episodes/1/content")).get("code").asText());
        assertEquals(400, send(user, "PUT", "/api/users/me", "{\"gender\":\"OTHER\"}").statusCode());
        assertEquals(200, send(user, "PUT", "/api/users/me", "{\"age\":25,\"gender\":\"male\"}").statusCode());
        assertEquals("MALE", userRepository.findById(user.id()).orElseThrow().getGender());
        assertEquals("ready", readyContent(user, 1).get("status").asText());
    }

    @Test
    void 나이가_없으면_1화에서_AGE_REQUIRED() throws Exception {
        TestUser user = createUser(null);
        createSeason(user);

        assertEquals("AGE_REQUIRED", error(get(user, "/episodes/1/content")).get("code").asText());

        HttpResponse<String> saved = send(user, "PUT", "/api/users/me", "{\"age\":17}");
        assertEquals(200, saved.statusCode());
        assertEquals(17, userRepository.findById(user.id()).orElseThrow().getAge());
        assertEquals("school", readyContent(user, 1).get("stageIntro").get("setting").asText());
    }

    @Test
    void 혼자_있기와_거절은_흐름을_solo로_만든다() throws Exception {
        TestUser user = createUser(30);
        Season season = createSeason(user);
        for (int order = 1; order <= 5; order++) {
            readyContent(user, order);
            Integer point = switch (order) {
                case 3 -> 1;
                case 5 -> 2;
                default -> null;
            };
            if (point != null) {
                post(user, "/episodes/" + order + "/choices", "{\"pointId\":" + point + ",\"breadId\":null}");
            }
            complete(user, order);
        }

        JsonNode ep6 = readyContent(user, 6);
        assertEquals("office", data(get(user, "/episodes/1/content")).get("stageIntro").get("setting").asText());
        // ② 혼자 있기 → 6화 1:1 상대는 친구
        JsonNode pair = ep6.get("blocks").get(2);
        assertEquals(breadOf(user, "friend"), pair.get("participants").get(1).get("breadId").asText());

        // 초기 호감(25)으로는 ③(기준 60)이 거절된다 → solo, 대체 장면 포함
        UUID anxious = candidateOf(user, "anxious");
        JsonNode rejected = data(post(user, "/episodes/6/choices", "{\"pointId\":3,\"breadId\":\"" + anxious + "\"}"));
        assertFalse(rejected.get("blocks").get(0).get("choiceResult").get("accepted").asBoolean());
        // 반응 장면 + FastAPI가 준 대체 장면(친구와의 pair) 하나
        assertEquals(2, rejected.get("blocks").size());
        JsonNode fallback = rejected.get("blocks").get(1);
        assertEquals("pair", fallback.get("type").asText());
        assertEquals("c3-f1", fallback.get("id").asText());
        assertEquals(breadOf(user, "friend"), fallback.get("participants").get(1).get("breadId").asText());
        assertEquals("solo", rejected.get("season").get("route").asText());
        Integer rejectCount = jdbcTemplate.queryForObject(
                "SELECT reject_count FROM ai_bread_relations WHERE season_id = ? AND target_bread_id = ?",
                Integer.class, season.pk(), anxious);
        assertEquals(1, rejectCount);
        // 다시 받은 6화 콘텐츠에도 choice 뒤에 반응 장면과 대체 장면이 이어진다.
        List<String> types6 = blockTypes(data(get(user, "/episodes/6/content")));
        int choiceAt = types6.indexOf("choice");
        assertEquals(List.of("choice_result", "pair"), types6.subList(choiceAt + 1, choiceAt + 3));
        complete(user, 6);

        String friend = breadOf(user, "friend");
        assertEquals(friend, pairFocus(readyContent(user, 7), user));
        complete(user, 7);
        readyContent(user, 8);
        post(user, "/episodes/8/choices", "{\"pointId\":4,\"breadId\":null}");
        complete(user, 8);
        readyContent(user, 9);
        complete(user, 9);
        readyContent(user, 10);
        complete(user, 10);
        // 혼자 흐름이면 최종 상대가 없다(AI 응답의 finalPartnerId와 관계없이 null).
        assertTrue(data(get(user, "/report")).get("finalPartnerId").isNull());
    }

    @Test
    void 일대일_장면에서만_피드백과_좋아요를_남긴다() throws Exception {
        TestUser user = createUser(22);
        createSeason(user);
        JsonNode ep1 = readyContent(user, 1);
        JsonNode pair = ep1.get("blocks").get(2);
        assertEquals("pair", pair.get("type").asText());
        String myLine = null;
        String partnerLine = null;
        for (JsonNode message : pair.get("messages")) {
            if (message.get("fromRepresentative").asBoolean()) {
                myLine = message.get("messageId").asText();
            } else if (!message.get("speakerId").isNull()) {
                partnerLine = message.get("messageId").asText();
            }
        }

        JsonNode feedback = data(post(user, "/episodes/1/scene-feedback", "{\"order\":1,\"blockId\":\"b3\","
                + "\"messageId\":\"" + myLine + "\",\"text\":\"이 대화는 부자연스럽다\"}"));
        assertEquals("이 대화는 부자연스럽다", feedback.get("text").asText());
        // 다시 남기면 글만 바뀐다.
        post(user, "/episodes/1/scene-feedback", "{\"blockId\":\"b3\",\"messageId\":\"" + myLine
                + "\",\"text\":\"조금 더 솔직했으면\"}");
        assertEquals(200, post(user, "/episodes/1/scene-likes", "{\"blockId\":\"b3\",\"messageId\":\""
                + myLine + "\",\"liked\":true}").statusCode());

        JsonNode reloaded = data(get(user, "/episodes/1/content"));
        assertEquals(1, reloaded.get("feedbacks").size());
        assertEquals("조금 더 솔직했으면", reloaded.get("feedbacks").get(0).get("text").asText());
        assertEquals(myLine, reloaded.get("likedMessageIds").get(0).asText());

        // 상대의 대사에는 피드백·좋아요 불가, pair가 아닌 블록에는 둘 다 불가, body order가 경로와 다르면 불가
        assertEquals("SCENE_NOT_MY_LINE", error(post(user, "/episodes/1/scene-feedback",
                "{\"blockId\":\"b3\",\"messageId\":\"" + partnerLine + "\",\"text\":\"x\"}")).get("code").asText());
        assertEquals("SCENE_NOT_MY_LINE", error(post(user, "/episodes/1/scene-likes",
                "{\"blockId\":\"b3\",\"messageId\":\"" + partnerLine + "\",\"liked\":true}")).get("code").asText());
        String groupLine = ep1.get("blocks").get(0).get("messages").get(1).get("messageId").asText();
        assertEquals("SCENE_NOT_PAIR", error(post(user, "/episodes/1/scene-likes",
                "{\"blockId\":\"b1\",\"messageId\":\"" + groupLine + "\",\"liked\":true}")).get("code").asText());
        assertEquals("SCENE_ORDER_MISMATCH", error(post(user, "/episodes/1/scene-likes", "{\"order\":2,\"blockId\":\"b3\","
                + "\"messageId\":\"" + myLine + "\",\"liked\":true}")).get("code").asText());
        assertEquals("SCENE_BLOCK_NOT_FOUND", error(post(user, "/episodes/1/scene-likes",
                "{\"blockId\":\"nope\",\"messageId\":\"" + myLine + "\",\"liked\":true}")).get("code").asText());
        assertEquals("FEEDBACK_TOO_LONG", error(post(user, "/episodes/1/scene-feedback", "{\"blockId\":\"b3\","
                + "\"messageId\":\"" + myLine + "\",\"text\":\"" + "가".repeat(201) + "\"}")).get("code").asText());

        // 끄면 목록에서 빠진다.
        post(user, "/episodes/1/scene-likes", "{\"blockId\":\"b3\",\"messageId\":\"" + myLine
                + "\",\"liked\":false}");
        assertEquals(0, data(get(user, "/episodes/1/content")).get("likedMessageIds").size());
    }

    @Test
    void 개발_전용_초기화() throws Exception {
        TestUser user = createUser(25);
        Season season = createSeason(user);
        readyContent(user, 1);
        complete(user, 1);

        assertEquals(200, send(user, "POST", "/api/dev/ai/seasons/current/story/reset", null).statusCode());

        Integer contents = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_episode_contents WHERE season_id = ?", Integer.class, season.pk());
        assertEquals(0, contents);
        assertEquals("STORY_NOT_STARTED", error(get(user, "/episodes/2/content")).get("code").asText());
        readyContent(user, 1);
    }

    // ------------------------------------------------------------------ helpers

    /** 4화부터는 회차 열기를 먼저 한다(이 클래스는 토큰 차감을 꺼 둔다). */
    private JsonNode readyContent(TestUser user, int order) throws Exception {
        if (order > 3) {
            HttpResponse<String> unlocked = post(user, "/episodes/" + order + "/unlock", null);
            assertEquals(200, unlocked.statusCode(), unlocked.body());
        }
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            HttpResponse<String> res = get(user, "/episodes/" + order + "/content");
            assertEquals(200, res.statusCode(), res.body());
            JsonNode content = data(res);
            String status = content.get("status").asText();
            if ("ready".equals(status)) {
                return content;
            }
            assertEquals("generating", status, content.toString());
            assertTrue(System.currentTimeMillis() < deadline, order + "화 생성이 끝나지 않습니다.");
            Thread.sleep(100);
        }
    }

    private JsonNode complete(TestUser user, int order) throws Exception {
        HttpResponse<String> res = post(user, "/episodes/" + order + "/complete", null);
        assertEquals(200, res.statusCode(), res.body());
        return data(res);
    }

    private String pairFocus(JsonNode content, TestUser user) {
        for (JsonNode block : content.get("blocks")) {
            if ("pair".equals(block.get("type").asText())) {
                for (JsonNode participant : block.get("participants")) {
                    if (!participant.get("isMe").asBoolean()) {
                        return participant.get("breadId").asText();
                    }
                }
                return null;
            }
        }
        throw new AssertionError("pair 블록이 없습니다.");
    }

    private UUID candidateOf(TestUser user, String role) throws Exception {
        return UUID.fromString(breadOf(user, role));
    }

    private String breadOf(TestUser user, String role) throws Exception {
        JsonNode cast = data(get(user, "/episodes/1/content")).get("blocks").get(0).get("participants");
        for (JsonNode participant : cast) {
            if (role.equals(participant.get("role").asText())) {
                return participant.get("breadId").asText();
            }
        }
        throw new AssertionError(role + " 출연진이 없습니다.");
    }

    private static List<Integer> intList(JsonNode array) {
        List<Integer> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asInt()));
        return values;
    }

    private static List<String> blockTypes(JsonNode content) {
        List<String> types = new ArrayList<>();
        content.get("blocks").forEach(b -> types.add(b.get("type").asText()));
        return types;
    }

    private JsonNode data(HttpResponse<String> res) throws IOException {
        assertEquals(200, res.statusCode(), res.body());
        return objectMapper.readTree(res.body()).get("data");
    }

    private JsonNode error(HttpResponse<String> res) throws IOException {
        assertTrue(res.statusCode() >= 400, res.body());
        return objectMapper.readTree(res.body());
    }

    private HttpResponse<String> get(TestUser user, String path) throws Exception {
        return send(user, "GET", "/api/ai/seasons" + path, null);
    }

    private HttpResponse<String> post(TestUser user, String path, String body) throws Exception {
        return send(user, "POST", "/api/ai/seasons" + path, body);
    }

    private HttpResponse<String> send(TestUser user, String method, String path, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + user.token())
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 시즌 생성에 필요한 설문 v2 결과를 실제 제출 API로 만들고 그 id를 돌려준다. */
    private String submitSurvey(TestUser user) throws Exception {
        JsonNode definition = data(send(user, "GET", "/api/surveys/v2/definition", null));
        StringBuilder answers = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            String q = String.format("q%02d", i);
            answers.append(i > 1 ? "," : "").append("{\"questionId\":\"").append(q)
                    .append("\",\"answerId\":\"").append(q).append("_a2\"}");
        }
        String body = "{\"definitionId\":\"" + definition.get("definitionId").asText()
                + "\",\"surveyVersion\":\"" + definition.get("surveyVersion").asText()
                + "\",\"clientSubmissionId\":\"" + UUID.randomUUID() + "\",\"answers\":[" + answers + "]}";
        HttpResponse<String> res = send(user, "POST", "/api/surveys/v2/submissions", body);
        assertTrue(res.statusCode() == 201 || res.statusCode() == 200, res.body());
        return objectMapper.readTree(res.body()).get("data").get("resultId").asText();
    }

    /**
     * POST /v1/seasons(representative 1명 + candidate 3명인 새 AI 시즌)와 GET /v1/seasons/{id}(시즌 상태 조회 시
     * revision 동기화용)만 흉내 낸다.
     */
    private static HttpServer startFakeAi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/v1/seasons", exchange -> {
                exchange.getRequestBody().readAllBytes();
                String[] path = exchange.getRequestURI().getPath().split("/");
                String seasonId = path.length > 3 ? path[3] : UUID.randomUUID().toString();
                StringBuilder characters = new StringBuilder("{\"id\":\"" + UUID.randomUUID()
                        + "\",\"role\":\"representative\",\"gender\":\"female\"}");
                for (int i = 0; i < 3; i++) {
                    characters.append(",{\"id\":\"").append(UUID.randomUUID())
                            .append("\",\"role\":\"candidate\",\"gender\":\"male\"}");
                }
                byte[] body = ("{\"season_id\":\"" + seasonId + "\",\"revision\":1,\"current_episode\":1,"
                        + "\"characters\":[" + characters + "],\"active_version_ids\":{}}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private TestUser createUser(Integer age) {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder()
                .googleId("story-" + unique)
                .name("빵")
                .email(unique + "@ai-story.test")
                .nickname("빵")
                .gender("FEMALE")
                .age(age)
                .build());
        userIds.add(user.getId());
        String token = jwtTokenProvider.createAccessToken(
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getNickname()));
        return new TestUser(user.getId(), token);
    }

    private Season createSeason(TestUser user) {
        AiSeason season = seasonRepository.save(new AiSeason(user.id(), 1, UUID.randomUUID(), 1, 1, null));
        seasonIds.add(season.getId());
        UUID representative = UUID.randomUUID();
        characterRepository.save(new AiSeasonCharacter(season, representative, AiCharacterRole.REPRESENTATIVE, "female"));
        List<UUID> candidates = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID id = UUID.randomUUID();
            characterRepository.save(new AiSeasonCharacter(season, id, AiCharacterRole.CANDIDATE, "male"));
            candidates.add(id);
        }
        return new Season(season.getId(), representative, candidates);
    }
}

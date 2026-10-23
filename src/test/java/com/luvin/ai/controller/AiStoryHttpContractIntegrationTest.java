package com.luvin.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luvin.LuvinBackendV2Application;
import com.luvin.ai.client.story.AiStoryStubClient;
import com.luvin.ai.client.story.ChoiceResultRequest;
import com.luvin.ai.client.story.EpisodeNoteRequest;
import com.luvin.ai.client.story.EpisodeRequest;
import com.luvin.ai.client.story.InterviewRequest;
import com.luvin.ai.client.story.SeasonReportRequest;
import com.luvin.ai.client.story.StageIntroRequest;
import com.luvin.ai.client.story.SummarizeRequest;
import com.luvin.ai.config.AiStoryConfig;
import com.luvin.ai.domain.AiCharacterRole;
import com.luvin.ai.domain.AiSeason;
import com.luvin.ai.domain.AiSeasonCharacter;
import com.luvin.ai.repository.AiSeasonCharacterRepository;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.common.security.AuthenticatedUser;
import com.luvin.common.security.JwtTokenProvider;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import com.sun.net.httpserver.HttpExchange;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring ↔ FastAPI 공통 데이터 계약(5장)의 wire 형식을 검증한다. app.ai.story.client=http로 실제 HTTP를 보내고,
 * 가짜 FastAPI는 요청을 계약 타입으로 읽어 stub 로직으로 답하면서 받은 원문 JSON을 남긴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = LuvinBackendV2Application.class)
@TestPropertySource(properties = "app.ai.story.client=http")
class AiStoryHttpContractIntegrationTest {

    private static final ObjectMapper CONTRACT = AiStoryConfig.STORY_OBJECT_MAPPER;
    private static final AiStoryStubClient STUB = new AiStoryStubClient();
    /** (경로, 요청 원문 JSON) 받은 순서대로. */
    private static final List<Map.Entry<String, JsonNode>> RECEIVED = Collections.synchronizedList(new ArrayList<>());
    private static final HttpServer FAKE_FASTAPI = startFakeFastApi();

    @DynamicPropertySource
    static void aiBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("app.ai.base-url", () -> "http://localhost:" + FAKE_FASTAPI.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        FAKE_FASTAPI.stop(0);
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
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ObjectMapper json = new ObjectMapper();
    private Long userId;
    private Long seasonPk;
    private String token;

    @AfterEach
    void tearDown() {
        List.of("ai_season_choices", "ai_bread_relations", "ai_episode_contents", "ai_season_story_reports",
                "partner_letters", "ai_story_feedbacks", "ai_story_message_likes", "ai_season_characters")
                .forEach(t -> jdbcTemplate.update("DELETE FROM " + t + " WHERE season_id = ?", seasonPk));
        jdbcTemplate.update("DELETE FROM ai_seasons WHERE id = ?", seasonPk);
        userRepository.deleteById(userId);
        RECEIVED.clear();
    }

    @Test
    void 계약_경로와_camelCase_형식으로_보내고_피드백이_다음_회차_입력에_들어간다() throws Exception {
        setUpUserAndSeason();

        JsonNode ep1 = readyContent(1);
        JsonNode stageIntro = last("/luvin/stage-intro");
        assertEquals("20대 초반", stageIntro.get("ageGroup").asText());
        assertEquals("adult", stageIntro.get("audience").asText());
        assertEquals(List.of("me", "avoidant", "anxious", "secure", "friend", "maker"),
                texts(stageIntro.get("cast"), "breadId"));

        JsonNode episode = last("/luvin/episode");
        assertTrue(episode.has("commonEvent") && episode.has("usedMaterials") && episode.has("referencePlot"));
        assertFalse(episode.has("common_event"));
        JsonNode context = episode.get("context");
        assertTrue(context.get("learnedProfile").has("recentAnswers"));
        JsonNode pairDesign = find(episode.get("design"), "type", "pair");
        assertEquals("me", pairDesign.get("participantIds").get(0).asText());
        assertTrue(pairDesign.has("pairTarget") && pairDesign.has("short"));
        JsonNode messengerDesign = find(episode.get("design"), "type", "messenger");
        assertFalse(messengerDesign.get("messenger").get("roomName").asText().isBlank());

        // 1화 1:1 장면의 내 빵 대사에 피드백 → 2화 생성 입력 learnedProfile.feedbacks로 들어간다.
        JsonNode pair = ep1.get("blocks").get(2);
        String myLine = null;
        for (JsonNode message : pair.get("messages")) {
            if (message.get("fromRepresentative").asBoolean()) {
                myLine = message.get("messageId").asText();
            }
        }
        assertEquals(200, send("POST", "/api/ai/seasons/episodes/1/scene-feedback",
                "{\"blockId\":\"b3\",\"messageId\":\"" + myLine + "\",\"text\":\"말투가 어색해\"}").statusCode());
        assertEquals(200, send("POST", "/api/ai/seasons/episodes/1/complete", null).statusCode());
        JsonNode summarize = last("/luvin/summarize");
        assertEquals("narrator", summarize.get("blocks").get(0).get("messages").get(0).get("speakerId").asText());
        assertTrue(last("/luvin/episode-note").has("settingScene"));

        readyContent(2);
        JsonNode feedbacks = last("/luvin/episode").get("context").get("learnedProfile").get("feedbacks");
        assertEquals(1, feedbacks.size());
        assertEquals("말투가 어색해", feedbacks.get(0).get("text").asText());
        assertEquals(1, feedbacks.get(0).get("order").asInt());
        send("POST", "/api/ai/seasons/episodes/2/complete", null);

        // 3화 ① 혼자 있기는 FastAPI를 부르지 않는다. 결과 반응 장면의 대사는 비어 있다.
        readyContent(3);
        JsonNode choiceDesign = find(last("/luvin/episode").get("design"), "type", "choice");
        assertEquals(1, choiceDesign.get("pointId").asInt());
        assertEquals(3, choiceDesign.get("candidateIds").size());
        int before = count("/luvin/choice-result");
        JsonNode alone = json.readTree(send("POST", "/api/ai/seasons/episodes/3/choices",
                "{\"pointId\":1,\"breadId\":null}").body()).get("data");
        assertEquals(before, count("/luvin/choice-result"));
        assertEquals(0, alone.get("blocks").get(0).get("messages").size());
    }

    // ------------------------------------------------------------------ helpers

    private void setUpUserAndSeason() {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder().googleId("contract-" + unique).name("빵")
                .email(unique + "@ai-contract.test").nickname("빵").gender("FEMALE").age(22).build());
        userId = user.getId();
        token = jwtTokenProvider.createAccessToken(new AuthenticatedUser(user.getId(), user.getEmail(), user.getNickname()));
        AiSeason season = seasonRepository.save(new AiSeason(userId, 1, UUID.randomUUID(), 1, 1, null));
        seasonPk = season.getId();
        characterRepository.save(new AiSeasonCharacter(season, UUID.randomUUID(), AiCharacterRole.REPRESENTATIVE, "female"));
        for (int i = 0; i < 3; i++) {
            characterRepository.save(new AiSeasonCharacter(season, UUID.randomUUID(), AiCharacterRole.CANDIDATE, "male"));
        }
    }

    private JsonNode readyContent(int order) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            HttpResponse<String> res = send("GET", "/api/ai/seasons/episodes/" + order + "/content", null);
            assertEquals(200, res.statusCode(), res.body());
            JsonNode content = json.readTree(res.body()).get("data");
            if ("ready".equals(content.get("status").asText())) {
                return content;
            }
            assertEquals("generating", content.get("status").asText(), content.toString());
            assertTrue(System.currentTimeMillis() < deadline, order + "화 생성이 끝나지 않습니다.");
            Thread.sleep(100);
        }
    }

    private HttpResponse<String> send(String method, String path, String body) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static JsonNode last(String path) {
        synchronized (RECEIVED) {
            for (int i = RECEIVED.size() - 1; i >= 0; i--) {
                if (RECEIVED.get(i).getKey().equals(path)) {
                    return RECEIVED.get(i).getValue();
                }
            }
        }
        throw new AssertionError(path + " 요청을 받지 못했습니다.");
    }

    private static int count(String path) {
        synchronized (RECEIVED) {
            return (int) RECEIVED.stream().filter(e -> e.getKey().equals(path)).count();
        }
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.get(field).asText())) {
                return node;
            }
        }
        throw new AssertionError(field + "=" + value + " 항목이 없습니다.");
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.get(field).asText()));
        return values;
    }

    private static HttpServer startFakeFastApi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/luvin/", AiStoryHttpContractIntegrationTest::handle);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        byte[] raw = exchange.getRequestBody().readAllBytes();
        RECEIVED.add(Map.entry(path, CONTRACT.readTree(raw)));
        Object response = switch (path) {
            case "/luvin/stage-intro" -> STUB.stageIntro(null, CONTRACT.readValue(raw, StageIntroRequest.class));
            case "/luvin/episode" -> STUB.writeEpisode(null, CONTRACT.readValue(raw, EpisodeRequest.class));
            case "/luvin/choice-result" -> STUB.writeChoiceResult(null, CONTRACT.readValue(raw, ChoiceResultRequest.class));
            case "/luvin/interview" -> STUB.writeInterview(null, CONTRACT.readValue(raw, InterviewRequest.class));
            case "/luvin/summarize" -> STUB.summarize(null, CONTRACT.readValue(raw, SummarizeRequest.class));
            case "/luvin/episode-note" -> STUB.writeEpisodeNote(null, CONTRACT.readValue(raw, EpisodeNoteRequest.class));
            case "/luvin/season-report" -> STUB.writeSeasonReport(null, CONTRACT.readValue(raw, SeasonReportRequest.class));
            default -> null;
        };
        byte[] body = CONTRACT.writeValueAsBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response == null ? 404 : 200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}

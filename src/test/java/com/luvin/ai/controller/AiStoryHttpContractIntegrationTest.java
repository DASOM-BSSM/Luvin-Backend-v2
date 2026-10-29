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
import com.luvin.token.domain.UserToken;
import com.luvin.token.repository.UserTokenRepository;
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
    /** 비동기 생성 job: Idempotency-Key → job. 같은 키로 다시 접수하면 같은 job을 돌려준다. */
    private static final Map<String, Map<String, Object>> JOBS = new java.util.concurrent.ConcurrentHashMap<>();
    /** 받은 Idempotency-Key(접수 순서대로, 다시 접수한 것 포함). */
    private static final List<String> JOB_KEYS = Collections.synchronizedList(new ArrayList<>());
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
    @Autowired
    private UserTokenRepository userTokenRepository;

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
        jdbcTemplate.update("DELETE FROM ai_episode_unlocks WHERE season_id = ?", seasonPk);
        jdbcTemplate.update("DELETE FROM ai_seasons WHERE id = ?", seasonPk);
        jdbcTemplate.update("DELETE FROM diary WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM diary_room WHERE owner_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_tokens WHERE member_id = ?", userId);
        jdbcTemplate.update("DELETE FROM token_histories WHERE member_id = ?", userId);
        userRepository.deleteById(userId);
        RECEIVED.clear();
    }

    @Test
    void 계약_경로와_camelCase_형식으로_보내고_피드백이_다음_회차_입력에_들어간다() throws Exception {
        setUpUserAndSeason();
        // 감정일기 16개 → 최근 15개가 원문 그대로 learnedProfile.styleSamples로 간다(최근 순).
        Long roomId = jdbcTemplate.queryForObject("INSERT INTO diary_room (owner_id, name, invite_code, max_member_count, "
                + "created_at, updated_at) VALUES (?, '방', ?, 6, now(), now()) RETURNING id", Long.class, userId,
                String.valueOf(userId % 100000000));
        for (int i = 1; i <= 16; i++) {
            jdbcTemplate.update("INSERT INTO diary (user_id, room_id, title, content, created_at, updated_at) "
                    + "VALUES (?, ?, '제목', ?, now() - make_interval(mins => ?), now())", userId, roomId, "일기 " + i, 100 - i);
        }

        JsonNode ep1 = readyContent(1);
        JsonNode stageIntro = last("/luvin/stage-intro");
        assertEquals("20대 초반", stageIntro.get("ageGroup").asText());
        assertEquals("adult", stageIntro.get("audience").asText());
        assertEquals(List.of("me", "avoidant", "anxious", "secure", "friend", "maker"),
                texts(stageIntro.get("cast"), "breadId"));
        // 역할 구분(계약 7장): me | friend | maker | candidate, 후보 셋은 attachmentStyle로 구분
        assertEquals(List.of("me", "candidate", "candidate", "candidate", "friend", "maker"),
                texts(stageIntro.get("cast"), "castRole"));
        // 성별(계약 5-0·5-1·7장): 친구만 사용자와 같고 나머지는 다르다.
        assertEquals(List.of("FEMALE", "MALE", "MALE", "MALE", "FEMALE", "MALE"), texts(stageIntro.get("cast"), "gender"));

        JsonNode episode = last("/luvin/episode");
        assertTrue(episode.has("commonEvent") && episode.has("usedMaterials") && episode.has("referencePlot"));
        assertFalse(episode.has("common_event"));
        JsonNode context = episode.get("context");
        assertEquals("FEMALE", context.get("cast").get(0).get("gender").asText());
        assertEquals("candidate", context.get("cast").get(1).get("castRole").asText());
        assertTrue(context.get("learnedProfile").has("recentAnswers"));
        List<String> styleSamples = texts(context.get("learnedProfile").get("styleSamples"));
        assertEquals(15, styleSamples.size());
        assertEquals("일기 16", styleSamples.get(0));
        // 말투(계약 7장): school이 아닌 배경은 모두 formal로 시작한다.
        assertEquals(List.of("formal", "formal", "formal", "formal", "formal"),
                texts(episode.get("relations"), "speechLevel"));
        JsonNode pairDesign = find(episode.get("design"), "type", "pair");
        assertEquals("me", pairDesign.get("participantIds").get(0).asText());
        assertTrue(pairDesign.has("pairTarget") && pairDesign.has("short"));

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
        complete(1);
        JsonNode summarize = last("/luvin/summarize");
        assertEquals("narrator", summarize.get("blocks").get(0).get("messages").get(0).get("speakerId").asText());
        JsonNode note1 = last("/luvin/episode-note");
        assertTrue(note1.has("settingScene"));
        // 1화 쪽지 발신 빵 = 1화 1:1 상대(첫 번째 후보), 아직 보낸 쪽지가 없다.
        assertTrue(java.util.Set.of("avoidant", "anxious", "secure")
                .contains(note1.get("sender").get("attachmentStyle").asText()));
        assertEquals(0, note1.get("previousNoteOpenings").size());

        readyContent(2);
        JsonNode messengerDesign = find(last("/luvin/episode").get("design"), "type", "messenger");
        assertEquals("group", messengerDesign.get("messenger").get("kind").asText());
        assertFalse(messengerDesign.get("messenger").get("roomName").asText().isBlank());
        // 이전 회차의 전환 카드 문구는 usedMaterials로 보낸다.
        String ep1Transition = ep1.get("blocks").get(1).get("transitionText").asText();
        assertTrue(texts(last("/luvin/episode").get("usedMaterials")).contains(ep1Transition));
        JsonNode feedbacks = last("/luvin/episode").get("context").get("learnedProfile").get("feedbacks");
        assertEquals(1, feedbacks.size());
        assertEquals("말투가 어색해", feedbacks.get(0).get("text").asText());
        assertEquals(1, feedbacks.get(0).get("order").asInt());
        complete(2);
        // 2화 쪽지 요청에는 1화 쪽지 첫 문장이 들어간다.
        assertEquals(1, last("/luvin/episode-note").get("previousNoteOpenings").size());

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
        assertTrue(alone.get("blocks").get(0).get("choiceResult").get("accepted").asBoolean());
    }

    /**
     * 1~10화를 끝까지 돌려 7개 endpoint를 모두 부르고, endpoint마다 처음 보낸 요청 JSON을
     * build/story-contract-examples/에 남긴다 — AI 쪽에 넘길 실제 요청 예시다.
     */
    @Test
    void 시즌_전체에서_7개_endpoint를_계약_형식으로_부르고_예시를_남긴다() throws Exception {
        setUpUserAndSeason();
        Map<Integer, String> picks = Map.of(3, "secure", 5, "avoidant", 6, "secure", 8, "friend");
        for (int order = 1; order <= 10; order++) {
            JsonNode content = readyContent(order);
            Integer point = switch (order) {
                case 3 -> 1;
                case 5 -> 2;
                case 6 -> 3;
                case 8 -> 4;
                default -> null;
            };
            if (point != null) {
                String breadId = participantId(content, picks.get(order));
                if (breadId == null) {
                    breadId = choiceCandidate(content, 0);
                }
                assertEquals(200, send("POST", "/api/ai/seasons/episodes/" + order + "/choices",
                        "{\"pointId\":" + point + ",\"breadId\":\"" + breadId + "\"}").statusCode());
            }
            complete(order);
        }

        // 쪽지 요청은 계약 5-6 필드 그대로다(계약에 없는 필드는 보내지 않는다). 시즌 요약은 최종 리포트(5-7)에만 간다.
        JsonNode finaleNote = last("/luvin/episode-note");
        assertEquals(10, finaleNote.get("order").asInt());
        assertEquals(java.util.Set.of("context", "order", "settingScene", "episodeSummary", "choice", "relations",
                "sender", "previousNoteOpenings"), fieldNames(finaleNote));
        assertEquals(java.util.Set.of("breadId", "name", "attachmentStyle", "statusText"),
                fieldNames(finaleNote.get("sender")));
        assertEquals(10, last("/luvin/season-report").get("episodeSummaries").size());

        java.nio.file.Path dir = java.nio.file.Path.of("build", "story-contract-examples");
        java.nio.file.Files.createDirectories(dir);
        for (String path : List.of("/luvin/stage-intro", "/luvin/episode", "/luvin/choice-result", "/luvin/interview",
                "/luvin/summarize", "/luvin/episode-note", "/luvin/season-report")) {
            JsonNode example = path.equals("/luvin/episode-note") ? finaleNote : first(path);
            java.nio.file.Files.writeString(dir.resolve(path.substring("/luvin/".length()) + ".json"),
                    CONTRACT.writerWithDefaultPrettyPrinter().writeValueAsString(example));
        }
    }

    @Test
    void 사화부터는_회차를_열어야_하고_처음_열_때만_토큰을_쓴다() throws Exception {
        setUpUserAndSeason();
        for (int order = 1; order <= 3; order++) {
            // 1~3화는 무료: 열기를 불러도 차감 없이 열린 응답이다.
            JsonNode free = json.readTree(send("POST", "/api/ai/seasons/episodes/" + order + "/unlock", null).body())
                    .get("data");
            assertTrue(free.get("unlocked").asBoolean());
            readyContent(order);
            if (order == 3) {
                send("POST", "/api/ai/seasons/episodes/3/choices", "{\"pointId\":1,\"breadId\":null}");
            }
            complete(order);
        }

        HttpResponse<String> locked = send("GET", "/api/ai/seasons/episodes/4/content", null);
        assertEquals(409, locked.statusCode());
        assertEquals("EPISODE_NOT_UNLOCKED", json.readTree(locked.body()).get("code").asText());

        HttpResponse<String> poor = send("POST", "/api/ai/seasons/episodes/4/unlock", null);
        assertEquals(409, poor.statusCode());
        assertEquals("INSUFFICIENT_TOKENS", json.readTree(poor.body()).get("code").asText());

        userTokenRepository.save(new UserToken(userId, 3, 0));
        JsonNode opened = json.readTree(send("POST", "/api/ai/seasons/episodes/4/unlock", null).body()).get("data");
        assertEquals(2, opened.get("tokenBalance").asInt());
        // 이미 연 회차는 다시 차감하지 않는다.
        JsonNode again = json.readTree(send("POST", "/api/ai/seasons/episodes/4/unlock", null).body()).get("data");
        assertEquals(2, again.get("tokenBalance").asInt());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM token_histories WHERE member_id = ? AND reason = '러빈지옥 4화 열기'",
                Integer.class, userId));
        assertEquals("ready", readyContent(4).get("status").asText());
    }

    // ------------------------------------------------------------------ helpers

    private void setUpUserAndSeason() {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder().googleId("contract-" + unique).name("빵")
                .email(unique + "@ai-contract.test").nickname("빵").gender("FEMALE").age(22)
                .personalityType("salt_bread").build());
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
        if (order > 3 && userTokenRepository.findByMemberId(userId).isEmpty()) {
            userTokenRepository.save(new UserToken(userId, 20, 0));
        }
        if (order > 3) {
            send("POST", "/api/ai/seasons/episodes/" + order + "/unlock", null);
        }
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

    /** 완료는 접수만 하고 백그라운드에서 끝난다 — 저장될 때까지 기다린다. */
    private void complete(int order) throws Exception {
        long choiceDeadline = System.currentTimeMillis() + 10_000;
        // 선택 결과 장면은 백그라운드에서 만든다 — 끝나야 회차를 완료할 수 있다.
        while (jdbcTemplate.queryForObject("SELECT count(*) FROM ai_season_choices WHERE season_id = ? "
                + "AND result_status = 'GENERATING'", Integer.class, seasonPk) > 0) {
            assertTrue(System.currentTimeMillis() < choiceDeadline, order + "화 선택 결과가 끝나지 않습니다.");
            Thread.sleep(100);
        }
        HttpResponse<String> res = send("POST", "/api/ai/seasons/episodes/" + order + "/complete", null);
        assertEquals(200, res.statusCode(), res.body());
        long deadline = System.currentTimeMillis() + 10_000;
        while (jdbcTemplate.queryForObject("SELECT count(*) FROM ai_episode_contents WHERE season_id = ? "
                + "AND episode_order = ? AND completed_at IS NOT NULL", Integer.class, seasonPk, order) == 0) {
            assertTrue(System.currentTimeMillis() < deadline, order + "화 완료가 끝나지 않습니다.");
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

    private static JsonNode first(String path) {
        synchronized (RECEIVED) {
            return RECEIVED.stream().filter(e -> e.getKey().equals(path)).map(Map.Entry::getValue).findFirst()
                    .orElseThrow(() -> new AssertionError(path + " 요청을 받지 못했습니다."));
        }
    }

    /** 1화 group 블록에서 그 역할(role)의 빵 id. 이 회차 선택 후보가 아니면 null을 돌려준다. */
    private String participantId(JsonNode content, String role) throws Exception {
        JsonNode group = readyContent(1).get("blocks").get(0).get("participants");
        String breadId = null;
        for (JsonNode participant : group) {
            if (role.equals(participant.get("role").asText())) {
                breadId = participant.get("breadId").asText();
            }
        }
        JsonNode choice = content.get("blocks").get(content.get("blocks").size() - 1).get("choice");
        for (JsonNode candidate : choice.get("candidates")) {
            if (candidate.get("breadId").asText().equals(breadId)) {
                return breadId;
            }
        }
        return null;
    }

    private static String choiceCandidate(JsonNode content, int index) {
        return content.get("blocks").get(content.get("blocks").size() - 1).get("choice").get("candidates")
                .get(index).get("breadId").asText();
    }

    private static java.util.Set<String> fieldNames(JsonNode node) {
        java.util.Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
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

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.asText()));
        return values;
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

    /**
     * 비동기 생성 job(POST /luvin/generation-jobs, GET /luvin/generation-jobs/{id})도 받는다. job의 input은 기존 동기
     * endpoint 요청과 같으므로 "/luvin/{task}"로 기록하고 같은 stub 로직으로 결과를 만든다. 첫 조회는 generating이다.
     */
    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        byte[] raw = exchange.getRequestBody().readAllBytes();
        if (path.startsWith("/luvin/generation-jobs")) {
            handleJob(exchange, path, raw);
            return;
        }
        respond(exchange, 200, answer(path, raw));
    }

    private static void handleJob(HttpExchange exchange, String path, byte[] raw) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            JsonNode submit = CONTRACT.readTree(raw);
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            JOB_KEYS.add(key);
            String taskPath = "/luvin/" + submit.get("task").asText();
            byte[] input = CONTRACT.writeValueAsBytes(submit.get("input"));
            Map<String, Object> job = JOBS.computeIfAbsent(key, k -> {
                Map<String, Object> created = new java.util.concurrent.ConcurrentHashMap<>();
                created.put("jobId", UUID.randomUUID().toString());
                created.put("status", "queued");
                created.put("attempt", 0);
                try {
                    created.put("pending", CONTRACT.valueToTree(answer(taskPath, input)));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                return created;
            });
            respond(exchange, 202, jobView(job));
            return;
        }
        String jobId = path.substring("/luvin/generation-jobs/".length());
        Map<String, Object> job = JOBS.values().stream().filter(j -> jobId.equals(j.get("jobId"))).findFirst()
                .orElse(null);
        if (job == null) {
            respond(exchange, 404, Map.of("detail", Map.of("code", "GENERATION_JOB_NOT_FOUND")));
            return;
        }
        if ("queued".equals(job.get("status"))) {
            job.put("status", "generating");
            job.put("attempt", 1);
        } else {
            job.put("status", "succeeded");
            job.put("result", job.get("pending"));
        }
        respond(exchange, 200, jobView(job));
    }

    private static Map<String, Object> jobView(Map<String, Object> job) {
        Map<String, Object> view = new java.util.HashMap<>();
        view.put("jobId", job.get("jobId"));
        view.put("status", job.get("status"));
        view.put("attempt", job.get("attempt"));
        view.put("result", job.get("result"));
        view.put("errorCode", null);
        view.put("code", null);
        return view;
    }

    private static void respond(HttpExchange exchange, int status, Object response) throws IOException {
        byte[] body = CONTRACT.writeValueAsBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response == null ? 404 : status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static Object answer(String path, byte[] raw) throws IOException {
        if (!path.startsWith("/luvin/generation-jobs")) {
            RECEIVED.add(Map.entry(path, CONTRACT.readTree(raw)));
        }
        return switch (path) {
            case "/luvin/stage-intro" -> STUB.stageIntro(null, CONTRACT.readValue(raw, StageIntroRequest.class));
            case "/luvin/episode" -> STUB.writeEpisode(null, CONTRACT.readValue(raw, EpisodeRequest.class));
            case "/luvin/choice-result" -> STUB.writeChoiceResult(null, CONTRACT.readValue(raw, ChoiceResultRequest.class));
            case "/luvin/interview" -> STUB.writeInterview(null, CONTRACT.readValue(raw, InterviewRequest.class));
            case "/luvin/summarize" -> STUB.summarize(null, CONTRACT.readValue(raw, SummarizeRequest.class));
            case "/luvin/episode-note" -> STUB.writeEpisodeNote(null, CONTRACT.readValue(raw, EpisodeNoteRequest.class));
            case "/luvin/season-report" -> STUB.writeSeasonReport(null, CONTRACT.readValue(raw, SeasonReportRequest.class));
            default -> null;
        };
    }
}

package com.luvin.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luvin.LuvinBackendV2Application;
import com.luvin.ai.client.story.AiStoryStubClient;
import com.luvin.ai.client.story.EpisodeNoteRequest;
import com.luvin.ai.client.story.EpisodeRequest;
import com.luvin.ai.client.story.StageIntroRequest;
import com.luvin.ai.client.story.SummarizeRequest;
import com.luvin.ai.config.AiStoryConfig;
import com.luvin.ai.domain.AiCharacterRole;
import com.luvin.ai.domain.AiSeason;
import com.luvin.ai.domain.AiSeasonCharacter;
import com.luvin.ai.repository.AiSeasonCharacterRepository;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.ai.service.story.AiStoryService;
import com.luvin.ai.service.story.AiStoryStateWriter;
import com.luvin.common.security.AuthenticatedUser;
import com.luvin.common.security.JwtTokenProvider;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 러빈지옥 생성을 FastAPI 비동기 생성 job(POST/GET /luvin/generation-jobs)으로 부르는 흐름을 실제 서버 + DB + 가짜 FastAPI로
 * 검증한다. 가짜 FastAPI는 Idempotency-Key별로 job을 하나만 만들고(같은 키·다른 입력이면 409), task별로 job을 붙잡아
 * 두거나(regenerating) 실패시킬 수 있다. 결과는 stub 로직으로 만든다. 실제 LLM은 부르지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = LuvinBackendV2Application.class)
@TestPropertySource(properties = {
        "app.ai.story.client=http",
        "app.ai.story.generation-mode=jobs",
        "app.ai.story.job-poll-interval=50ms",
        "app.ai.story.token-charge-enabled=false"
})
class AiStoryGenerationJobIntegrationTest {

    private static final ObjectMapper CONTRACT = AiStoryConfig.STORY_OBJECT_MAPPER;
    private static final AiStoryStubClient STUB = new AiStoryStubClient();
    private static final List<String> SEASON_TABLES = List.of(
            "ai_season_choices", "ai_bread_relations", "ai_episode_contents", "ai_season_story_reports",
            "partner_letters", "ai_story_feedbacks", "ai_story_message_likes", "ai_episode_unlocks",
            "ai_season_characters", "ai_episode_progresses");

    /** Idempotency-Key → job. */
    private static final Map<String, FakeJob> JOBS = new ConcurrentHashMap<>();
    /** 받은 접수의 Idempotency-Key(다시 접수한 것 포함, 순서대로). */
    private static final List<String> SUBMITTED_KEYS = Collections.synchronizedList(new ArrayList<>());
    /** 이 task의 job은 regenerating(attempt 2)에 머문다. */
    private static final Set<String> HOLD = ConcurrentHashMap.newKeySet();
    /** 이 task의 job은 첫 조회에 GENERATION_REJECTED로 실패한다. */
    private static final Set<String> FAIL = ConcurrentHashMap.newKeySet();
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
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AiStoryService storyService;
    @Autowired
    private AiStoryStateWriter stateWriter;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> seasonIds = new ArrayList<>();

    private record TestUser(Long id, String token) {
    }

    private record Season(Long pk, UUID aiSeasonId) {
    }

    @BeforeEach
    void resetFakeAi() {
        JOBS.clear();
        SUBMITTED_KEYS.clear();
        HOLD.clear();
        FAIL.clear();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        HOLD.clear();
        Thread.sleep(200); // 붙잡아 둔 job을 기다리던 백그라운드 생성이 끝나게 둔다.
        for (Long seasonPk : seasonIds) {
            SEASON_TABLES.forEach(t -> jdbcTemplate.update("DELETE FROM " + t + " WHERE season_id = ?", seasonPk));
            jdbcTemplate.update("DELETE FROM ai_seasons WHERE id = ?", seasonPk);
        }
        userRepository.deleteAllById(userIds);
    }

    @Test
    void 회차_생성은_job으로_접수하고_다시_쓰는_중이면_progress에_보인다() throws Exception {
        TestUser user = createUser();
        Season season = createSeason(user);
        HOLD.add("episode");

        JsonNode first = content(user, 1);
        assertEquals("generating", first.get("status").asText());
        JsonNode held = awaitContent(user, 1, c -> c.path("progress").path("regenerating").asBoolean());
        assertEquals("episode", held.get("progress").get("stage").asText(), held.toString());

        HOLD.remove("episode");
        JsonNode ready = awaitContent(user, 1, c -> "ready".equals(c.get("status").asText()));
        assertTrue(ready.get("progress").isNull());

        long contentPk = contentPk(season, 1);
        String base = "luvin:" + season.aiSeasonId() + ":c" + contentPk + ":gen1:";
        assertEquals(List.of(base + "stage-intro:a1", base + "episode:a1"), SUBMITTED_KEYS);
    }

    @Test
    void 재시작으로_같은_회차를_다시_돌리면_같은_키로_접수해_새_job을_만들지_않는다() throws Exception {
        TestUser user = createUser();
        Season season = createSeason(user);
        HOLD.add("episode");
        content(user, 1);
        awaitContent(user, 1, c -> c.path("progress").path("regenerating").asBoolean());

        // 서버가 재시작된 것처럼 끝나지 않은 생성을 다시 돌린다(지금 돌던 것과 같은 회차).
        storyService.resumeInterrupted();
        Thread.sleep(300);
        HOLD.remove("episode");
        awaitContent(user, 1, c -> "ready".equals(c.get("status").asText()));

        long contentPk = contentPk(season, 1);
        String episodeKey = "luvin:" + season.aiSeasonId() + ":c" + contentPk + ":gen1:episode:a1";
        assertTrue(SUBMITTED_KEYS.stream().filter(episodeKey::equals).count() >= 2, SUBMITTED_KEYS.toString());
        assertEquals(1, JOBS.values().stream().filter(j -> j.task.equals("episode")).count());
    }

    @Test
    void job이_실패하면_failed이고_다시_요청하면_새_회차의_새_키로_생성하며_이전_회차_결과는_버린다() throws Exception {
        TestUser user = createUser();
        Season season = createSeason(user);
        FAIL.add("episode");

        content(user, 1);
        JsonNode failed = awaitContent(user, 1, c -> "failed".equals(c.get("status").asText()));
        assertEquals("STORY_GENERATION_FAILED", failed.get("errorCode").asText());
        long contentPk = contentPk(season, 1);
        String round1 = "luvin:" + season.aiSeasonId() + ":c" + contentPk + ":gen1:";
        assertTrue(SUBMITTED_KEYS.containsAll(List.of(round1 + "episode:a1", round1 + "episode:a2")),
                SUBMITTED_KEYS.toString());

        // 다시 시도: 2회차로 새로 생성한다. 그사이 늦게 온 1회차 결과는 저장하지 않는다.
        FAIL.remove("episode");
        HOLD.add("episode");
        content(user, 1);
        awaitContent(user, 1, c -> c.path("progress").path("regenerating").asBoolean());
        stateWriter.saveGenerated(contentPk, 1, "{}", null, "[]");
        assertEquals("GENERATING", jdbcTemplate.queryForObject(
                "SELECT status FROM ai_episode_contents WHERE id = ?", String.class, contentPk));

        HOLD.remove("episode");
        JsonNode ready = awaitContent(user, 1, c -> "ready".equals(c.get("status").asText()));
        assertTrue(ready.get("blocks").size() > 0);
        String round2 = "luvin:" + season.aiSeasonId() + ":c" + contentPk + ":gen2:";
        assertTrue(SUBMITTED_KEYS.contains(round2 + "episode:a1"), SUBMITTED_KEYS.toString());
    }

    @Test
    void 회차_완료는_단계와_다시_쓰는_중인지가_보이고_중복으로_돌아도_한_번만_반영된다() throws Exception {
        TestUser user = createUser();
        Season season = createSeason(user);
        content(user, 1);
        awaitContent(user, 1, c -> "ready".equals(c.get("status").asText()));
        HOLD.add("summarize");

        JsonNode accepted = data(post(user, "/episodes/1/complete")).get("completion");
        assertEquals("completing", accepted.get("status").asText());
        JsonNode held = awaitStory(user, s -> s.path("completion").path("progress").path("regenerating").asBoolean());
        assertEquals("summarize", held.get("completion").get("progress").get("stage").asText());

        // 같은 완료를 다른 실행이 한 번 더 돌린다(재시작·다른 서버). 같은 job을 이어서 보고, 결과는 한 번만 반영된다.
        storyService.resumeInterrupted();
        Thread.sleep(300);
        HOLD.remove("summarize");
        JsonNode done = awaitStory(user, s -> s.get("completion").isNull()
                && s.get("completedOrders").toString().equals("[1]"));
        assertEquals(2, done.get("currentOrder").asInt());
        Thread.sleep(500);
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM partner_letters WHERE season_id = ? "
                + "AND episode_order = 1", Integer.class, season.pk()));
        assertEquals(1, JOBS.values().stream().filter(j -> j.task.equals("summarize")).count());
        assertEquals(1, JOBS.values().stream().filter(j -> j.task.equals("episode-note")).count());
    }

    @Test
    void 이미_실패한_job이나_다른_입력의_키는_건너뛰고_번호를_붙인_새_키로_접수한다() throws Exception {
        TestUser user = createUser();
        Season season = createSeason(user);
        content(user, 1);
        awaitContent(user, 1, c -> "ready".equals(c.get("status").asText()));
        long contentPk = contentPk(season, 1);
        String base = "luvin:" + season.aiSeasonId() + ":c" + contentPk + ":done1:";
        // 이전 실행에서 이미 실패한 summarize job, 다른 입력으로 쓰인 episode-note 키
        JOBS.put(base + "summarize:a1", FakeJob.failed("summarize"));
        JOBS.put(base + "episode-note:a1", new FakeJob("episode-note", "{\"other\":true}", null));

        post(user, "/episodes/1/complete");
        awaitStory(user, s -> s.get("completedOrders").toString().equals("[1]"));
        assertTrue(SUBMITTED_KEYS.containsAll(List.of(base + "summarize:a1", base + "summarize:a1:n1",
                base + "episode-note:a1", base + "episode-note:a1:n1")), SUBMITTED_KEYS.toString());
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode content(TestUser user, int order) throws Exception {
        HttpResponse<String> res = send(user, "GET", "/api/ai/seasons/episodes/" + order + "/content");
        assertEquals(200, res.statusCode(), res.body());
        return data(res);
    }

    private JsonNode awaitContent(TestUser user, int order, Predicate<JsonNode> done) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        JsonNode last;
        do {
            last = content(user, order);
            if (done.test(last)) {
                return last;
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("기다린 상태가 되지 않았습니다: " + last);
    }

    private JsonNode awaitStory(TestUser user, Predicate<JsonNode> done) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        JsonNode last;
        do {
            last = data(send(user, "GET", "/api/ai/seasons/me")).get("story");
            if (done.test(last)) {
                return last;
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("기다린 상태가 되지 않았습니다: " + last);
    }

    private HttpResponse<String> post(TestUser user, String path) throws Exception {
        HttpResponse<String> res = send(user, "POST", "/api/ai/seasons" + path);
        assertEquals(200, res.statusCode(), res.body());
        return res;
    }

    private long contentPk(Season season, int order) {
        return jdbcTemplate.queryForObject("SELECT id FROM ai_episode_contents WHERE season_id = ? AND episode_order = ?",
                Long.class, season.pk(), order);
    }

    private JsonNode data(HttpResponse<String> res) throws IOException {
        return objectMapper.readTree(res.body()).get("data");
    }

    private HttpResponse<String> send(TestUser user, String method, String path)
            throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + user.token())
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private TestUser createUser() {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder().googleId("job-" + unique).name("빵")
                .email(unique + "@ai-job.test").nickname("빵").gender("FEMALE").age(22).build());
        userIds.add(user.getId());
        return new TestUser(user.getId(), jwtTokenProvider.createAccessToken(
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getNickname())));
    }

    private Season createSeason(TestUser user) {
        UUID aiSeasonId = UUID.randomUUID();
        AiSeason season = seasonRepository.save(new AiSeason(user.id(), 1, aiSeasonId, 1, 1, null));
        seasonIds.add(season.getId());
        characterRepository.save(new AiSeasonCharacter(season, UUID.randomUUID(), AiCharacterRole.REPRESENTATIVE, "female"));
        for (int i = 0; i < 3; i++) {
            characterRepository.save(new AiSeasonCharacter(season, UUID.randomUUID(), AiCharacterRole.CANDIDATE, "male"));
        }
        return new Season(season.getId(), aiSeasonId);
    }

    // ------------------------------------------------------------------ 가짜 FastAPI

    private static final class FakeJob {
        final UUID id = UUID.randomUUID();
        final String task;
        final String input;
        final Object result;
        volatile String status = "queued";
        volatile int attempt;
        volatile String errorCode;

        FakeJob(String task, String input, Object result) {
            this.task = task;
            this.input = input;
            this.result = result;
        }

        static FakeJob failed(String task) {
            FakeJob job = new FakeJob(task, null, null);
            job.status = "failed";
            job.attempt = 2;
            job.errorCode = "GENERATION_REJECTED";
            return job;
        }

        /** 조회할 때마다 한 단계씩 진행한다. */
        synchronized void advance() {
            if ("succeeded".equals(status) || "failed".equals(status)) {
                return;
            }
            if (FAIL.contains(task)) {
                status = "failed";
                attempt = 2;
                errorCode = "GENERATION_REJECTED";
            } else if (HOLD.contains(task)) {
                status = "regenerating";
                attempt = 2;
            } else if ("queued".equals(status)) {
                status = "generating";
                attempt = 1;
            } else {
                status = "succeeded";
            }
        }

        Map<String, Object> view() {
            Map<String, Object> view = new HashMap<>();
            view.put("jobId", id.toString());
            view.put("status", status);
            view.put("attempt", attempt);
            view.put("result", "succeeded".equals(status) ? result : null);
            view.put("errorCode", errorCode);
            return view;
        }
    }

    private static HttpServer startFakeAi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/luvin/generation-jobs", AiStoryGenerationJobIntegrationTest::handleJob);
            // 시즌 상태 조회(GET /me)가 revision 동기화로 부른다.
            server.createContext("/v1/seasons", exchange -> {
                exchange.getRequestBody().readAllBytes();
                String[] path = exchange.getRequestURI().getPath().split("/");
                respond(exchange, 200, Map.of("season_id", path[3], "revision", 1, "current_episode", 1,
                        "characters", List.of(), "active_version_ids", Map.of()));
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handleJob(HttpExchange exchange) throws IOException {
        byte[] raw = exchange.getRequestBody().readAllBytes();
        if ("POST".equals(exchange.getRequestMethod())) {
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            SUBMITTED_KEYS.add(key);
            JsonNode submit = CONTRACT.readTree(raw);
            String task = submit.get("task").asText();
            String input = CONTRACT.writeValueAsString(submit.get("input"));
            FakeJob existing = JOBS.get(key);
            if (existing != null && existing.input != null && !existing.input.equals(input)) {
                respond(exchange, 409, Map.of("detail", Map.of("code", "IDEMPOTENCY_CONFLICT")));
                return;
            }
            FakeJob job = JOBS.computeIfAbsent(key, k -> new FakeJob(task, input, answer(task, submit.get("input"))));
            respond(exchange, 202, job.view());
            return;
        }
        String jobId = exchange.getRequestURI().getPath().substring("/luvin/generation-jobs/".length());
        FakeJob job = JOBS.values().stream().filter(j -> j.id.toString().equals(jobId)).findFirst().orElse(null);
        if (job == null) {
            respond(exchange, 404, Map.of("detail", Map.of("code", "GENERATION_JOB_NOT_FOUND")));
            return;
        }
        job.advance();
        respond(exchange, 200, job.view());
    }

    private static Object answer(String task, JsonNode input) {
        try {
            return switch (task) {
                case "stage-intro" -> STUB.stageIntro(null, CONTRACT.treeToValue(input, StageIntroRequest.class));
                case "episode" -> STUB.writeEpisode(null, CONTRACT.treeToValue(input, EpisodeRequest.class));
                case "summarize" -> STUB.summarize(null, CONTRACT.treeToValue(input, SummarizeRequest.class));
                case "episode-note" -> STUB.writeEpisodeNote(null, CONTRACT.treeToValue(input, EpisodeNoteRequest.class));
                default -> throw new IllegalArgumentException("이 테스트에서 쓰지 않는 task: " + task);
            };
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = CONTRACT.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}

package com.luvin.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luvin.LuvinBackendV2Application;
import com.luvin.ai.domain.AiEpisodeProgress;
import com.luvin.ai.domain.AiJobStatus;
import com.luvin.ai.domain.AiSeason;
import com.luvin.ai.repository.AiEpisodeProgressRepository;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.ai.security.OwnerSubjectProvider;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * job SSE 중계(GET /api/ai/jobs/{jobId}/events)를 실제 서버 + DB로 검증한다.
 * AI 서비스 자리에는 같은 JVM에 띄운 가짜 SSE 서버를 둔다(app.ai.base-url을 그쪽으로 돌린다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = LuvinBackendV2Application.class)
class AiJobEventStreamIntegrationTest {

    private static final HttpServer FAKE_AI = startFakeAi();
    /** jobId → 가짜 AI가 받은 요청 헤더 (X-Owner-Subject, Last-Event-ID). */
    private static final Map<String, Map<String, String>> RECEIVED = new ConcurrentHashMap<>();
    private static final UUID MISSING_AT_AI_JOB = UUID.randomUUID();
    /** 가짜 AI의 GET /v1/jobs/{id}가 돌려주는 완료 결과 version. */
    private static final UUID RESULT_VERSION_ID = UUID.randomUUID();
    /** 가짜 AI가 400(잘못된 cursor)으로 거절하는 Last-Event-ID. */
    private static final String INVALID_CURSOR = "not-a-cursor";

    private static final String AI_EVENTS = ": keep-alive\n\n"
            + "id: 1\nevent: segment_start\ndata: {\"segment\":\"scene\"}\n\n"
            + "id: 2\nevent: delta\ndata: {\"segment\":\"scene\",\"text\":\"{\\\"lines\\\":[\"}\n\n"
            + "id: 3\nevent: segment_end\ndata: {\"segment\":\"scene\"}\n\n"
            + "id: 4\nevent: completed\ndata: {\"version_id\":\"v1\",\"revision\":2}\n\n"
            + "id: 5\nevent: delta\ndata: {\"segment\":\"scene\",\"text\":\"completed 뒤라 중계되면 안 됨\"}\n\n";

    @DynamicPropertySource
    static void aiBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("app.ai.base-url", () -> "http://localhost:" + FAKE_AI.getAddress().getPort());
    }

    @LocalServerPort
    private int port;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AiSeasonRepository seasonRepository;
    @Autowired
    private AiEpisodeProgressRepository progressRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private OwnerSubjectProvider ownerSubjectProvider;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdSeasonIds = new ArrayList<>();
    private final List<Long> createdProgressIds = new ArrayList<>();

    private TestUser owner;
    private TestUser outsider;

    private record TestUser(Long id, String token) {
    }

    @BeforeEach
    void setUp() {
        owner = createUser("주인");
        outsider = createUser("외부인");
    }

    @AfterEach
    void tearDown() {
        progressRepository.deleteAllById(createdProgressIds);
        seasonRepository.deleteAllById(createdSeasonIds);
        userRepository.deleteAllById(createdUserIds);
        RECEIVED.clear();
    }

    @AfterAll
    static void stopFakeAi() {
        FAKE_AI.stop(0);
    }

    @Test
    void 내_job이면_AI_이벤트를_id_이름_data_그대로_중계하고_completed에서_닫는다() throws Exception {
        UUID jobId = createJob(owner);

        HttpResponse<String> res = stream(owner, jobId, "Last-Event-ID", "0", null);

        assertEquals(200, res.statusCode());
        assertTrue(res.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
        String body = res.body();
        assertTrue(body.contains("id:1\nevent:segment_start\ndata:{\"segment\":\"scene\"}\n\n"), body);
        assertTrue(body.contains("id:2\nevent:delta\ndata:{\"segment\":\"scene\",\"text\":\"{\\\"lines\\\":[\"}\n\n"), body);
        assertTrue(body.contains("id:4\nevent:completed\ndata:{\"version_id\":\"v1\",\"revision\":2}\n\n"), body);
        assertFalse(body.contains("id:5"), body);
        assertFalse(body.contains("stream_error"), body);

        Map<String, String> sentToAi = RECEIVED.get(jobId.toString());
        assertEquals(ownerSubjectProvider.resolve(owner.id()), sentToAi.get("X-Owner-Subject"));
        assertEquals("0", sentToAi.get("Last-Event-ID"));
    }

    @Test
    void completed를_앱에_보내기_전에_job_완료를_DB에_반영해_바로_조회할_수_있다() throws Exception {
        UUID jobId = createJob(owner);

        assertEquals(200, stream(owner, jobId, null, null, null).statusCode());

        // 진행 상태의 nextPollAt이 비어 있어 스케줄러는 이 job을 건드리지 않는다 — 반영됐다면 SSE 중계가 한 것이다.
        AiEpisodeProgress progress = progressRepository.findByJobId(jobId).orElseThrow();
        assertEquals(AiJobStatus.SUCCEEDED, progress.getJobStatus());
        assertEquals(RESULT_VERSION_ID, progress.getVersionId());
    }

    @Test
    void after_event_id_query도_AI에_Last_Event_ID로_넘긴다() throws Exception {
        UUID jobId = createJob(owner);

        assertEquals(200, stream(owner, jobId, null, null, "3").statusCode());
        assertEquals("3", RECEIVED.get(jobId.toString()).get("Last-Event-ID"));
    }

    @Test
    void 남의_job이면_AI를_부르지_않고_JSON_404() throws Exception {
        UUID jobId = createJob(owner);

        HttpResponse<String> res = stream(outsider, jobId, null, null, null);

        assertEquals(404, res.statusCode());
        JsonNode json = objectMapper.readTree(res.body());
        assertFalse(json.path("success").asBoolean(true));
        assertFalse(RECEIVED.containsKey(jobId.toString()));
    }

    @Test
    void AI가_job을_못_찾으면_stream_error로_알리고_닫는다() throws Exception {
        UUID jobId = createJob(owner, MISSING_AT_AI_JOB);

        HttpResponse<String> res = stream(owner, jobId, null, null, null);

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("event:stream_error\ndata:{\"error_code\":\"JOB_NOT_FOUND\"}\n\n"), res.body());
    }

    @Test
    void AI가_Last_Event_ID를_400으로_거절하면_INVALID_LAST_EVENT_ID로_알린다() throws Exception {
        UUID jobId = createJob(owner);

        HttpResponse<String> res = stream(owner, jobId, "Last-Event-ID", INVALID_CURSOR, null);

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("event:stream_error\ndata:{\"error_code\":\"INVALID_LAST_EVENT_ID\"}\n\n"), res.body());
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/ai/jobs/" + UUID.randomUUID() + "/events"))
                .header("Accept", "text/event-stream").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode());
    }

    // ------------------------------------------------------------------ helpers

    private HttpResponse<String> stream(TestUser who, UUID jobId, String header, String headerValue, String afterEventId)
            throws IOException, InterruptedException {
        String query = afterEventId == null ? "" : "?after_event_id=" + afterEventId;
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/ai/jobs/" + jobId + "/events" + query))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + who.token())
                .header("Accept", "text/event-stream")
                .GET();
        if (header != null) {
            request.header(header, headerValue);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private TestUser createUser(String nickname) {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder()
                .googleId("test-" + unique)
                .name(nickname)
                .email(unique + "@ai-job-sse.test")
                .nickname(nickname)
                .build());
        createdUserIds.add(user.getId());
        String token = jwtTokenProvider.createAccessToken(
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getNickname()));
        return new TestUser(user.getId(), token);
    }

    private UUID createJob(TestUser who) {
        return createJob(who, UUID.randomUUID());
    }

    private UUID createJob(TestUser who, UUID jobId) {
        AiSeason season = seasonRepository.save(new AiSeason(who.id(), 1, UUID.randomUUID(), 1, 1, null));
        createdSeasonIds.add(season.getId());
        AiEpisodeProgress progress = progressRepository.save(new AiEpisodeProgress(season, 1, jobId));
        createdProgressIds.add(progress.getId());
        return jobId;
    }

    private static HttpServer startFakeAi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/v1/jobs/", AiJobEventStreamIntegrationTest::handleFakeAi);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * GET /v1/jobs/{jobId}/events 흉내. MISSING_AT_AI_JOB이면 404, Last-Event-ID가 INVALID_CURSOR면 400,
     * 아니면 AI_EVENTS를 보내고 닫는다. GET /v1/jobs/{jobId}(polling)는 항상 succeeded를 돌려준다.
     */
    private static void handleFakeAi(HttpExchange exchange) throws IOException {
        String[] path = exchange.getRequestURI().getPath().split("/");
        String jobId = path[3];
        if (path.length == 4) {
            try (OutputStream out = exchange.getResponseBody()) {
                writeJson(exchange, out, 200, "{\"job_id\":\"" + jobId + "\",\"status\":\"succeeded\",\"status_url\":\"/v1/jobs/"
                        + jobId + "\",\"result_version_id\":\"" + RESULT_VERSION_ID + "\",\"error_code\":null}");
            }
            return;
        }
        Map<String, String> headers = new ConcurrentHashMap<>();
        headers.put("X-Owner-Subject", String.valueOf(exchange.getRequestHeaders().getFirst("X-Owner-Subject")));
        String lastEventId = exchange.getRequestHeaders().getFirst("Last-Event-ID");
        if (lastEventId != null) {
            headers.put("Last-Event-ID", lastEventId);
        }
        RECEIVED.put(jobId, headers);

        try (OutputStream out = exchange.getResponseBody()) {
            if (jobId.equals(MISSING_AT_AI_JOB.toString())) {
                writeJson(exchange, out, 404, "{\"error_code\":\"JOB_NOT_FOUND\",\"message\":\"job not found\"}");
                return;
            }
            if (INVALID_CURSOR.equals(lastEventId)) {
                writeJson(exchange, out, 400, "{\"error_code\":\"INVALID_CURSOR\",\"message\":\"invalid Last-Event-ID\"}");
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            out.write(AI_EVENTS.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeJson(HttpExchange exchange, OutputStream out, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        out.write(body);
    }
}

package com.luvin.diary.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luvin.LuvinBackendV2Application;
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
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 감정일기 푸시 3종(QUESTION_CREATED, QUESTION_ANSWERED, DIARY_COMMENT)과 발송 시간 제한(KST 06:00~23:59)을 실제 서버 + DB +
 * 가짜 Expo 전송 API로 확인한다. 시계는 테스트가 바꿔 낀다(기본 KST 12:00).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = LuvinBackendV2Application.class)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "app.push.client=expo")
@Import(DiaryPushIntegrationTest.ClockConfig.class)
class DiaryPushIntegrationTest {

    private static final List<JsonNode> PUSHED = Collections.synchronizedList(new ArrayList<>());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpServer FAKE_EXPO = startFakeExpo();
    private static final Instant NOON = OffsetDateTime.parse("2026-11-25T12:00:00+09:00").toInstant();
    private static final AtomicReference<Instant> NOW = new AtomicReference<>(NOON);

    @TestConfiguration
    static class ClockConfig {
        /** 발송 시간 제한이 읽는 시계. NOW를 바꾸면 바로 반영된다. */
        @Bean
        Clock pushClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return Clock.fixed(NOW.get(), zone);
                }

                @Override
                public Instant instant() {
                    return NOW.get();
                }
            };
        }
    }

    @DynamicPropertySource
    static void expoUrl(DynamicPropertyRegistry registry) {
        registry.add("app.push.expo-url", () -> "http://localhost:" + FAKE_EXPO.getAddress().getPort() + "/push/send");
    }

    @AfterAll
    static void stop() {
        FAKE_EXPO.stop(0);
    }

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();
    private TestUser owner;
    private TestUser member;
    private TestUser third;
    private long roomId;

    private record TestUser(Long id, String token, String device) {
    }

    @BeforeEach
    void setUp() {
        NOW.set(NOON);
        PUSHED.clear();
        cleanDiaryTables();
        owner = createUser("방장계정");
        member = createUser("멤버계정");
        third = createUser("셋째계정");
        roomId = data(call(HttpMethod.POST, "/api/diary-rooms", owner,
                Map.of("name", "우리방", "maxMemberCount", 6, "ownerNickname", "방장"))).get("id").asLong();
        addMember(member, "멤버");
        addMember(third, "셋째");
        for (TestUser user : List.of(owner, member, third)) {
            registerDevice(user, user.device());
        }
    }

    @AfterEach
    void tearDown() {
        NOW.set(NOON);
        cleanDiaryTables();
        for (Long id : createdUserIds) {
            jdbc.update("DELETE FROM push_tokens WHERE member_id = ?", id);
        }
        userRepository.deleteAllById(createdUserIds);
    }

    @Test
    void 질문을_만들면_다른_그룹원에게_그룹_닉네임으로_알리고_만든_사람에게는_보내지_않는다() throws Exception {
        String gone = "ExponentPushToken[Gone-" + UUID.randomUUID() + "]";
        registerDevice(third, gone);

        long questionId = createQuestion(member);

        for (TestUser to : List.of(owner, third)) {
            JsonNode push = awaitPush(to.device(), "QUESTION_CREATED");
            assertEquals("우리방", push.get("title").asText());
            assertEquals("멤버님이 질문을 만들었어요", push.get("body").asText());
            assertEquals("diary-activity", push.get("channelId").asText());
            JsonNode data = push.get("data");
            assertEquals(2, data.size());
            assertTrue(data.get("roomId").isNumber());
            assertEquals(roomId, data.get("roomId").asLong());
        }
        // Expo가 DeviceNotRegistered를 돌려준 토큰은 지운다.
        awaitPush(gone, "QUESTION_CREATED");
        awaitTokenRemoved(gone);
        quietly();
        assertTrue(pushes(member.device()).isEmpty());
        assertTrue(questionId > 0);
    }

    @Test
    void 답을_처음_올리면_다른_그룹원에게_알리고_수정은_알리지_않는다() throws Exception {
        long questionId = createQuestion(owner);
        awaitPush(member.device(), "QUESTION_CREATED");
        PUSHED.clear();

        assertEquals(200, answer(owner, questionId, "첫 답").getStatusCode().value());
        for (TestUser to : List.of(member, third)) {
            JsonNode push = awaitPush(to.device(), "QUESTION_ANSWERED");
            assertEquals("방장님이 답변을 올렸어요", push.get("body").asText());
            assertEquals("diary-activity", push.get("channelId").asText());
            assertEquals(roomId, push.get("data").get("roomId").asLong());
            assertEquals(2, push.get("data").size());
        }

        assertEquals(200, answer(owner, questionId, "고친 답").getStatusCode().value());
        quietly();
        assertEquals(1, pushes(member.device()).size());
        assertEquals(1, pushes(third.device()).size());
        assertTrue(pushes(owner.device()).isEmpty());
    }

    @Test
    void 내_일기에_댓글이_달리면_작성자에게만_알리고_내가_단_댓글은_알리지_않는다() throws Exception {
        long diaryId = data(call(HttpMethod.POST, "/api/diaries", member,
                Map.of("roomId", roomId, "title", "제목", "content", "내용"))).get("diaryId").asLong();

        comment(owner, diaryId);
        JsonNode push = awaitPush(member.device(), "DIARY_COMMENT");
        assertEquals("우리방", push.get("title").asText());
        assertEquals("방장님이 내 기분에 댓글을 남겼어요", push.get("body").asText());
        assertEquals("diary-activity", push.get("channelId").asText());
        JsonNode data = push.get("data");
        assertEquals(3, data.size());
        assertTrue(data.get("roomId").isNumber());
        assertTrue(data.get("diaryId").isNumber());
        assertEquals(roomId, data.get("roomId").asLong());
        assertEquals(diaryId, data.get("diaryId").asLong());

        comment(member, diaryId); // 내 일기에 내가 단 댓글
        quietly();
        assertEquals(1, pushes(member.device()).size());
        assertTrue(pushes(owner.device()).isEmpty());
        assertTrue(pushes(third.device()).isEmpty());
    }

    @Test
    void 한국_시간_자정부터_05시59분까지_생긴_알림은_버리고_06시부터_보낸다() throws Exception {
        long diaryId = data(call(HttpMethod.POST, "/api/diaries", member,
                Map.of("roomId", roomId, "title", "제목", "content", "내용"))).get("diaryId").asLong();

        for (String time : List.of("00:00:00", "05:59:59")) {
            NOW.set(OffsetDateTime.parse("2026-11-25T" + time + "+09:00").toInstant());
            comment(owner, diaryId);
        }
        quietly();
        assertTrue(pushes(member.device()).isEmpty());

        // 버린 알림은 06시가 되어도 몰아서 보내지 않는다. 06시 이후 새로 생긴 것만 간다.
        NOW.set(OffsetDateTime.parse("2026-11-25T06:00:00+09:00").toInstant());
        comment(third, diaryId);
        JsonNode push = awaitPush(member.device(), "DIARY_COMMENT");
        assertEquals("셋째님이 내 기분에 댓글을 남겼어요", push.get("body").asText());
        quietly();
        assertEquals(1, pushes(member.device()).size());
    }

    // ------------------------------------------------------------------ helpers

    private long createQuestion(TestUser who) {
        ResponseEntity<String> res = call(HttpMethod.POST, "/api/diary-rooms/" + roomId + "/questions", who, null);
        assertTrue(res.getStatusCode().is2xxSuccessful(), res.getBody());
        return data(res).get("questionId").asLong();
    }

    private ResponseEntity<String> answer(TestUser who, long questionId, String content) throws InterruptedException {
        // 질문 생성(스텁 AI)이 끝나 ready가 된 뒤에 답할 수 있다.
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            ResponseEntity<String> res = call(HttpMethod.POST,
                    "/api/diary-rooms/" + roomId + "/questions/" + questionId + "/answers", who,
                    Map.of("content", content));
            if (res.getStatusCode().value() != 409 || System.currentTimeMillis() > deadline) {
                return res;
            }
            Thread.sleep(100);
        }
    }

    private void comment(TestUser who, long diaryId) {
        ResponseEntity<String> res = call(HttpMethod.POST, "/api/diaries/" + diaryId + "/comments", who,
                Map.of("content", "댓글"));
        assertEquals(200, res.getStatusCode().value(), res.getBody());
    }

    private void addMember(TestUser who, String nickname) {
        ResponseEntity<String> res = call(HttpMethod.POST, "/api/diary-rooms/" + roomId + "/members", owner,
                Map.of("userId", who.id(), "nickname", nickname));
        assertEquals(200, res.getStatusCode().value(), res.getBody());
    }

    private void registerDevice(TestUser who, String device) {
        ResponseEntity<String> res = call(HttpMethod.POST, "/api/push-tokens", who,
                Map.of("token", device, "platform", "android"));
        assertEquals(200, res.getStatusCode().value(), res.getBody());
    }

    private TestUser createUser(String nickname) {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder()
                .googleId("push-" + unique)
                .name(nickname)
                .email(unique + "@push.test")
                .nickname(nickname)
                .build());
        createdUserIds.add(user.getId());
        String token = jwtTokenProvider.createAccessToken(
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getNickname()));
        return new TestUser(user.getId(), token, "ExponentPushToken[ok-" + unique + "]");
    }

    private ResponseEntity<String> call(HttpMethod method, String path, TestUser who, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(who.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private static JsonNode data(ResponseEntity<String> res) {
        try {
            return JSON.readTree(res.getBody()).get("data");
        } catch (IOException e) {
            throw new IllegalStateException(res.getBody(), e);
        }
    }

    private static List<JsonNode> pushes(String device) {
        synchronized (PUSHED) {
            return PUSHED.stream().filter(p -> p.get("to").asText().equals(device)).toList();
        }
    }

    /** 알림은 커밋 뒤 따로 보내므로 응답보다 조금 늦게 온다. */
    private static JsonNode awaitPush(String device, String type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            for (JsonNode push : pushes(device)) {
                if (type.equals(push.get("data").get("type").asText())) {
                    return push;
                }
            }
            assertTrue(System.currentTimeMillis() < deadline, type + " 알림이 오지 않았습니다: " + device);
            Thread.sleep(50);
        }
    }

    private void awaitTokenRemoved(String device) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (jdbc.queryForObject("SELECT count(*) FROM push_tokens WHERE token = ?", Integer.class, device) > 0) {
            assertTrue(System.currentTimeMillis() < deadline, "DeviceNotRegistered 토큰이 지워지지 않았습니다.");
            Thread.sleep(50);
        }
    }

    /** 보내지 않아야 하는 알림이 늦게라도 오지 않는지 잠시 기다린다. */
    private static void quietly() throws InterruptedException {
        Thread.sleep(700);
    }

    private void cleanDiaryTables() {
        jdbc.execute("delete from user_learning_signal");
        jdbc.execute("delete from room_question_answer");
        jdbc.execute("delete from room_question");
        jdbc.execute("delete from diary_reaction");
        jdbc.execute("delete from diary_comment");
        jdbc.execute("delete from diary");
        jdbc.execute("delete from diary_room_member");
        jdbc.execute("delete from diary_room");
    }

    /** 가짜 Expo 전송 API. 토큰에 "Gone"이 들어 있으면 DeviceNotRegistered ticket을 돌려준다(순서는 요청과 같다). */
    private static HttpServer startFakeExpo() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/push/send", DiaryPushIntegrationTest::handlePush);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handlePush(HttpExchange exchange) throws IOException {
        JsonNode messages = JSON.readTree(exchange.getRequestBody().readAllBytes());
        List<Map<String, Object>> tickets = new ArrayList<>();
        for (JsonNode message : messages) {
            PUSHED.add(message);
            tickets.add(message.get("to").asText().contains("Gone")
                    ? Map.of("status", "error", "message", "not registered",
                            "details", Map.of("error", "DeviceNotRegistered"))
                    : Map.of("status", "ok", "id", UUID.randomUUID().toString()));
        }
        byte[] body = JSON.writeValueAsBytes(Map.of("data", tickets));
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}

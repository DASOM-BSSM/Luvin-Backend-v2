package com.luvin.diary.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luvin.LuvinBackendV2Application;
import com.luvin.common.security.AuthenticatedUser;
import com.luvin.common.security.JwtTokenProvider;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 오늘의 밸런스 게임(/api/daily_questions) API를 실제 서버 + DB로 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = LuvinBackendV2Application.class)
@AutoConfigureTestRestTemplate
class BalanceGameApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> createdUserIds = new ArrayList<>();

    private record TestUser(Long id, String token) {
    }

    private record Res(int status, JsonNode body) {
        JsonNode data() {
            return body.get("data");
        }
    }

    @BeforeEach
    void setUp() {
        cleanTables();
    }

    @AfterEach
    void tearDown() {
        cleanTables();
        userRepository.deleteAllById(createdUserIds);
    }

    @Test
    void 오늘_질문은_모든_사용자에게_같고_고르기_전에는_결과가_없다() {
        TestUser a = createUser("가");
        TestUser b = createUser("나");

        Res first = call(HttpMethod.GET, "/api/daily_questions/today", a, null);
        assertEquals(200, first.status(), String.valueOf(first.body()));
        assertTrue(first.body().get("success").asBoolean());
        JsonNode view = first.data();
        assertEquals(LocalDate.now(ZoneId.of("Asia/Seoul")).toString(), view.get("questionDate").asText());
        assertEquals(2, view.get("options").size());
        assertFalse(view.get("answered").asBoolean());
        assertTrue(view.get("selectedOptionId").isNull());
        assertEquals(0, view.get("results").size());
        assertEquals(0, view.get("totalCount").asLong());

        Res second = call(HttpMethod.GET, "/api/daily_questions/today", b, null);
        assertEquals(view.get("questionId").asLong(), second.data().get("questionId").asLong());
        assertEquals(1, count("select count(*) from daily_questions"));
        assertEquals(2, count("select count(*) from daily_question_options"));
    }

    @Test
    void 고르면_결과가_채워지고_다시_고를_수_없다() {
        TestUser a = createUser("가");
        TestUser b = createUser("나");
        JsonNode today = call(HttpMethod.GET, "/api/daily_questions/today", a, null).data();
        long questionId = today.get("questionId").asLong();
        long optionA = today.get("options").get(0).get("optionId").asLong();
        long optionB = today.get("options").get(1).get("optionId").asLong();

        Res answered = answer(a, questionId, optionA);
        assertEquals(200, answered.status(), String.valueOf(answered.body()));
        assertTrue(answered.data().get("answered").asBoolean());
        assertEquals(optionA, answered.data().get("selectedOptionId").asLong());
        assertEquals(1, answered.data().get("totalCount").asLong());
        assertEquals(100.0, answered.data().get("results").get(0).get("percentage").asDouble());

        assertEquals(200, answer(b, questionId, optionB).status());
        JsonNode mine = call(HttpMethod.GET, "/api/daily_questions/today", a, null).data();
        assertEquals(2, mine.get("totalCount").asLong());
        assertEquals(1, mine.get("results").get(0).get("voteCount").asLong());
        assertEquals(50.0, mine.get("results").get(1).get("percentage").asDouble());

        Res again = answer(a, questionId, optionB);
        assertEquals(409, again.status());
        assertEquals("DAILY_QUESTION_ALREADY_ANSWERED", again.body().get("code").asText());
        assertEquals(optionA, call(HttpMethod.GET, "/api/daily_questions/today", a, null)
                .data().get("selectedOptionId").asLong());
    }

    @Test
    void 잘못된_보기와_없는_질문은_코드로_거절한다() {
        TestUser a = createUser("가");
        long questionId = call(HttpMethod.GET, "/api/daily_questions/today", a, null).data().get("questionId").asLong();
        long otherQuestionOption = insertOtherQuestionOption();

        Res wrongOption = answer(a, questionId, otherQuestionOption);
        assertEquals(400, wrongOption.status());
        assertEquals("DAILY_QUESTION_INVALID_OPTION", wrongOption.body().get("code").asText());

        Res missingOption = call(HttpMethod.POST, "/api/daily_questions/" + questionId + "/answer", a, Map.of());
        assertEquals(400, missingOption.status());
        assertEquals("DAILY_QUESTION_INVALID_OPTION", missingOption.body().get("code").asText());

        Res noQuestion = answer(a, 999_999_999L, otherQuestionOption);
        assertEquals(404, noQuestion.status());
        assertEquals("DAILY_QUESTION_NOT_FOUND", noQuestion.body().get("code").asText());
    }

    @Test
    void 로그인하지_않으면_401() {
        ResponseEntity<String> res = restTemplate.getForEntity("/api/daily_questions/today", String.class);
        assertEquals(401, res.getStatusCode().value());
    }

    @Test
    void 동시에_처음_조회해도_오늘_질문은_하나만_생긴다() throws Exception {
        TestUser a = createUser("가");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Res>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                calls.add(() -> call(HttpMethod.GET, "/api/daily_questions/today", a, null));
            }
            Map<Long, Integer> ids = new HashMap<>();
            for (Future<Res> future : pool.invokeAll(calls)) {
                Res res = future.get();
                assertEquals(200, res.status(), String.valueOf(res.body()));
                ids.merge(res.data().get("questionId").asLong(), 1, Integer::sum);
            }
            assertEquals(1, ids.size());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, count("select count(*) from daily_questions"));
        assertEquals(2, count("select count(*) from daily_question_options"));
    }

    // ------------------------------------------------------------------ helpers

    /** 어제 날짜의 다른 질문과 보기 하나를 직접 넣는다. @return 그 보기 id */
    private long insertOtherQuestionOption() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        Long qid = jdbc.queryForObject("insert into daily_questions (content, question_date, source) "
                + "values ('다른 질문', ?, 'PRESET') returning daily_question_id", Long.class, yesterday);
        return jdbc.queryForObject("insert into daily_question_options (daily_question_id, content) "
                + "values (?, '다른 보기') returning option_id", Long.class, qid);
    }

    private Res answer(TestUser who, long questionId, long optionId) {
        return call(HttpMethod.POST, "/api/daily_questions/" + questionId + "/answer", who,
                Map.of("selectedOption", optionId));
    }

    private TestUser createUser(String nickname) {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder()
                .googleId("test-" + unique)
                .name(nickname)
                .email(unique + "@balance.test")
                .nickname(nickname)
                .build());
        createdUserIds.add(user.getId());
        String token = jwtTokenProvider.createAccessToken(
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getNickname()));
        return new TestUser(user.getId(), token);
    }

    private Res call(HttpMethod method, String path, TestUser who, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(who.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> res = restTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
        try {
            JsonNode json = res.getBody() == null ? objectMapper.createObjectNode() : objectMapper.readTree(res.getBody());
            return new Res(res.getStatusCode().value(), json);
        } catch (Exception e) {
            throw new IllegalStateException("응답 파싱 실패: " + res.getBody(), e);
        }
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private void cleanTables() {
        jdbc.execute("delete from daily_question_answers");
        jdbc.execute("delete from daily_question_options");
        jdbc.execute("delete from daily_questions");
    }
}

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 감정일기(일기·공유방·이모지 반응·피드) API를 실제 서버 + DB로 검증한다.
 * 구글 로그인 대신 JwtTokenProvider로 직접 발급한 토큰을 쓴다. CI의 Postgres 서비스 컨테이너에서 돈다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = LuvinBackendV2Application.class)
@AutoConfigureTestRestTemplate
class DiaryApiIntegrationTest {

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

    private TestUser owner;
    private TestUser member;
    private TestUser outsider;

    private record TestUser(Long id, String token) {
    }

    private record Res(int status, JsonNode body) {
        JsonNode data() {
            return body.get("data");
        }

        String message() {
            return body.path("message").asText(null);
        }
    }

    @BeforeEach
    void setUp() {
        cleanDiaryTables();
        owner = createUser("방장", "salt_bread");
        member = createUser("멤버", null);
        outsider = createUser("외부인", null);
    }

    @AfterEach
    void tearDown() {
        cleanDiaryTables();
        userRepository.deleteAllById(createdUserIds);
    }

    // ------------------------------------------------------------------ 일기

    @Test
    void 일기는_방_멤버만_작성할_수_있고_항상_방에_속한다() {
        long roomId = createRoomWithMember();

        Res created = createDiary(member, roomId);
        assertEquals(200, created.status());
        assertEquals(roomId, created.data().get("roomId").asLong());
        assertFalse(created.data().has("visibility"));

        assertEquals(403, createDiary(outsider, roomId).status());
        assertEquals(404, createDiary(member, 999_999L).status());
        assertEquals(400, call(HttpMethod.POST, "/api/diaries", member,
                Map.of("title", "제목", "content", "내용")).status());
    }

    @Test
    void 일기_상세는_작성자와_방_멤버만_볼_수_있다() {
        long roomId = createRoomWithMember();
        long diaryId = createDiary(member, roomId).data().get("diaryId").asLong();

        assertEquals(200, call(HttpMethod.GET, "/api/diaries/" + diaryId, owner, null).status());
        assertEquals(403, call(HttpMethod.GET, "/api/diaries/" + diaryId, outsider, null).status());
    }

    @Test
    void 일기_수정_삭제는_작성자만_가능하고_삭제하면_반응과_댓글도_지워진다() {
        long roomId = createRoomWithMember();
        long diaryId = createDiary(member, roomId).data().get("diaryId").asLong();
        react(owner, diaryId, "❤️");
        call(HttpMethod.POST, "/api/diaries/" + diaryId + "/comments", owner, Map.of("content", "댓글"));

        assertEquals(403, call(HttpMethod.PUT, "/api/diaries/" + diaryId, owner,
                Map.of("title", "x", "content", "x")).status());
        Res updated = call(HttpMethod.PUT, "/api/diaries/" + diaryId, member, Map.of("title", "수정", "content", "수정"));
        assertEquals(200, updated.status());
        assertEquals("수정", updated.data().get("title").asText());

        assertEquals(403, call(HttpMethod.DELETE, "/api/diaries/" + diaryId, owner, null).status());
        assertEquals(200, call(HttpMethod.DELETE, "/api/diaries/" + diaryId, member, null).status());
        assertEquals(0, count("diary"));
        assertEquals(0, count("diary_reaction"));
        assertEquals(0, count("diary_comment"));
    }

    // ------------------------------------------------------------------ 이모지 반응

    @Test
    void 이모지_반응은_추가_교체_취소_순서로_토글된다() {
        long diaryId = createDiary(member, createRoomWithMember()).data().get("diaryId").asLong();

        Res added = react(owner, diaryId, "❤️");
        assertReaction(added, "❤", true, 1);

        Res changed = react(owner, diaryId, "😂");
        assertReaction(changed, "😂", true, 1);

        Res cancelled = react(owner, diaryId, "😂");
        assertReaction(cancelled, null, false, 0);
    }

    @Test
    void 하트는_FE0F_유무와_상관없이_같은_반응으로_취소된다() {
        long diaryId = createDiary(member, createRoomWithMember()).data().get("diaryId").asLong();

        assertReaction(react(owner, diaryId, "❤"), "❤", true, 1);
        assertReaction(react(owner, diaryId, "❤️"), null, false, 0);
    }

    @Test
    void 이모지_키보드의_아무_이모지나_가능하고_여러_명의_반응이_합산된다() {
        long diaryId = createDiary(member, createRoomWithMember()).data().get("diaryId").asLong();

        assertReaction(react(owner, diaryId, "👨‍👩‍👧"), "👨‍👩‍👧", true, 1);
        assertReaction(react(member, diaryId, "🔥"), "🔥", true, 2);
    }

    @Test
    void 이모지가_1개가_아니면_400_방_멤버가_아니면_403() {
        long diaryId = createDiary(member, createRoomWithMember()).data().get("diaryId").asLong();

        for (String invalid : List.of("🔥🔥", "ㅋ", "1", "")) {
            assertEquals(400, react(owner, diaryId, invalid).status(), "emoji=" + invalid);
        }
        assertEquals(403, react(outsider, diaryId, "😊").status());
        assertEquals(404, react(owner, 999_999L, "😊").status());
    }

    @Test
    void 방을_나간_뒤에도_자기_반응은_취소할_수_있다() {
        long roomId = createRoomWithMember();
        long diaryId = createDiary(owner, roomId).data().get("diaryId").asLong();
        react(member, diaryId, "😊");
        call(HttpMethod.DELETE, "/api/diary-rooms/" + roomId + "/members/me", member, null);

        assertReaction(react(member, diaryId, "😊"), null, false, 0);
        assertEquals(403, react(member, diaryId, "😊").status());
    }

    @Test
    void 같은_이모지를_동시에_여러_번_눌러도_반응은_최대_1개만_남는다() throws Exception {
        long diaryId = createDiary(member, createRoomWithMember()).data().get("diaryId").asLong();
        int requests = 20;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < requests; i++) {
            pool.submit(() -> {
                start.await();
                statuses.add(react(owner, diaryId, "😍").status());
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertTrue(statuses.stream().allMatch(s -> s == 200), "statuses=" + statuses);
        Integer rows = jdbc.queryForObject(
                "select count(*) from diary_reaction where diary_id = ? and user_id = ?", Integer.class, diaryId, owner.id());
        assertTrue(rows == 0 || rows == 1, "rows=" + rows);
    }

    // ------------------------------------------------------------------ 피드

    @Test
    void 공유방_피드는_방_멤버만_보고_작성자_정보와_내_반응이_포함된다() {
        long roomId = createRoomWithMember();
        long diaryId = createDiary(owner, roomId).data().get("diaryId").asLong();
        react(member, diaryId, "🥺");

        Res feed = call(HttpMethod.GET, "/api/diary-rooms/" + roomId + "/diaries", member, null);
        assertEquals(200, feed.status());
        JsonNode item = feed.data().get(0);
        assertEquals("방장", item.get("authorNickname").asText());
        assertEquals("salt_bread", item.get("authorBreadType").asText());
        assertEquals("🥺", item.get("emoji").asText());
        assertTrue(item.get("liked").asBoolean());
        assertEquals(1, item.get("likeCount").asLong());
        assertFalse(item.get("isMine").asBoolean());

        assertEquals(403, call(HttpMethod.GET, "/api/diary-rooms/" + roomId + "/diaries", outsider, null).status());
    }

    @Test
    void 내_방들_피드는_내가_속한_방의_일기만_최신순으로_준다() {
        long roomA = createRoomWithMember();
        long roomB = createRoom(owner, "방B");
        createDiary(member, roomA);
        createDiary(owner, roomB);

        assertEquals(2, call(HttpMethod.GET, "/api/diaries/community", owner, null).data().size());
        JsonNode memberFeed = call(HttpMethod.GET, "/api/diaries/community", member, null).data();
        assertEquals(1, memberFeed.size());
        assertEquals(roomA, memberFeed.get(0).get("roomId").asLong());
        assertEquals(0, call(HttpMethod.GET, "/api/diaries/community", outsider, null).data().size());
    }

    // ------------------------------------------------------------------ 공유방

    @Test
    void 방을_만들면_방장이_OWNER로_등록되고_내_방_목록은_최근_것이_먼저다() {
        long first = createRoom(owner, "첫번째");
        long second = createRoom(owner, "두번째");

        assertEquals("OWNER", jdbc.queryForObject(
                "select role from diary_room_member where room_id = ? and user_id = ?", String.class, first, owner.id()));
        JsonNode rooms = call(HttpMethod.GET, "/api/diary-rooms", owner, null).data();
        assertEquals(second, rooms.get(0).get("id").asLong());
        assertEquals(first, rooms.get(1).get("id").asLong());
    }

    @Test
    void 방_수정은_방장만_가능하고_설명도_반영된다() {
        long roomId = createRoomWithMember();

        assertEquals(403, call(HttpMethod.PUT, "/api/diary-rooms/" + roomId, member, Map.of("name", "x")).status());
        Res updated = call(HttpMethod.PUT, "/api/diary-rooms/" + roomId, owner, Map.of("name", "새이름", "description", "새설명"));
        assertEquals(200, updated.status());
        assertEquals("새이름", updated.data().get("name").asText());
        assertEquals("새설명", jdbc.queryForObject("select description from diary_room where id = ?", String.class, roomId));
    }

    @Test
    void 방을_삭제하면_방의_일기_반응_댓글_멤버가_함께_지워지고_다른_방은_남는다() {
        long roomA = createRoomWithMember();
        long roomB = createRoom(owner, "남는방");
        long diaryId = createDiary(member, roomA).data().get("diaryId").asLong();
        createDiary(owner, roomB);
        react(owner, diaryId, "😊");
        call(HttpMethod.POST, "/api/diaries/" + diaryId + "/comments", owner, Map.of("content", "댓글"));

        assertEquals(403, call(HttpMethod.DELETE, "/api/diary-rooms/" + roomA, member, null).status());
        assertEquals(200, call(HttpMethod.DELETE, "/api/diary-rooms/" + roomA, owner, null).status());

        assertEquals(1, count("diary_room"));
        assertEquals(1, count("diary"));
        assertEquals(0, count("diary_reaction"));
        assertEquals(0, count("diary_comment"));
        assertEquals(1, count("diary_room_member"));
    }

    @Test
    void 멤버_관리_초대는_방장만_나가기는_방장_불가_강퇴는_방장만() {
        long roomId = createRoom(owner, "방");
        String members = "/api/diary-rooms/" + roomId + "/members";

        assertEquals(403, call(HttpMethod.POST, members, outsider, Map.of("userId", member.id())).status());
        Res added = call(HttpMethod.POST, members, owner, Map.of("userId", member.id()));
        assertEquals(200, added.status());
        assertTrue(added.data().get("isMember").asBoolean());
        assertEquals(2, added.data().get("memberCount").asLong());
        assertEquals(404, call(HttpMethod.POST, members, owner, Map.of("userId", 999_999L)).status());

        assertEquals(2, call(HttpMethod.GET, members, member, null).data().size());
        assertEquals(403, call(HttpMethod.GET, members, outsider, null).status());

        assertEquals(400, call(HttpMethod.DELETE, members + "/me", owner, null).status());
        assertEquals(403, call(HttpMethod.DELETE, members + "/kick?userId=" + owner.id(), member, null).status());
        assertEquals(400, call(HttpMethod.DELETE, members + "/kick?userId=" + owner.id(), owner, null).status());
        Res kicked = call(HttpMethod.DELETE, members + "/kick?userId=" + member.id(), owner, null);
        assertEquals(200, kicked.status());
        assertEquals(member.id(), kicked.data().get("userId").asLong());
        assertEquals(1, count("diary_room_member"));
    }

    @Test
    void 토큰이_없으면_401() {
        ResponseEntity<String> res = restTemplate.getForEntity("/api/diaries/community", String.class);
        assertEquals(401, res.getStatusCode().value());
    }

    // ------------------------------------------------------------------ helpers

    private TestUser createUser(String nickname, String breadType) {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder()
                .googleId("test-" + unique)
                .name(nickname)
                .email(unique + "@diary.test")
                .nickname(nickname)
                .personalityType(breadType)
                .build());
        createdUserIds.add(user.getId());
        String token = jwtTokenProvider.createAccessToken(
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getNickname()));
        return new TestUser(user.getId(), token);
    }

    private long createRoom(TestUser who, String name) {
        Res res = call(HttpMethod.POST, "/api/diary-rooms", who, Map.of("name", name));
        assertEquals(200, res.status(), String.valueOf(res.body()));
        return res.data().get("id").asLong();
    }

    /** owner가 방을 만들고 member를 초대한다. */
    private long createRoomWithMember() {
        long roomId = createRoom(owner, "방");
        assertEquals(200, call(HttpMethod.POST, "/api/diary-rooms/" + roomId + "/members", owner,
                Map.of("userId", member.id())).status());
        return roomId;
    }

    private Res createDiary(TestUser who, long roomId) {
        return call(HttpMethod.POST, "/api/diaries", who, Map.of("roomId", roomId, "title", "제목", "content", "내용"));
    }

    private Res react(TestUser who, long diaryId, String emoji) {
        return call(HttpMethod.POST, "/api/diaries/" + diaryId + "/like", who, Map.of("emoji", emoji));
    }

    private void assertReaction(Res res, String emoji, boolean liked, long likeCount) {
        assertEquals(200, res.status(), String.valueOf(res.body()));
        JsonNode data = res.data();
        if (emoji == null) {
            assertTrue(data.get("emoji").isNull());
        } else {
            assertEquals(emoji, data.get("emoji").asText());
        }
        assertEquals(liked, data.get("liked").asBoolean());
        assertEquals(likeCount, data.get("likeCount").asLong());
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

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private void cleanDiaryTables() {
        jdbc.execute("delete from diary_reaction");
        jdbc.execute("delete from diary_comment");
        jdbc.execute("delete from diary");
        jdbc.execute("delete from diary_room_member");
        jdbc.execute("delete from diary_room");
    }
}

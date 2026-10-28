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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    void 공개범위_컬럼이_남은_옛_diary_테이블도_V14_마이그레이션_뒤에는_일기를_쓸_수_있다() throws IOException {
        long roomId = createRoomWithMember();
        // 스테이징처럼 10-04 이전 구조를 재현한다: visibility NOT NULL + CHECK, room_id NULL 허용
        jdbc.execute("alter table diary add column visibility varchar(255) not null"
                + " check (visibility in ('PRIVATE','ROOM','PUBLIC'))");
        jdbc.execute("alter table diary alter column room_id drop not null");
        try {
            assertEquals(500, createDiary(member, roomId).status());

            jdbc.execute(StreamUtils.copyToString(
                    new ClassPathResource("db/migration/V14__drop_legacy_diary_visibility.sql").getInputStream(),
                    StandardCharsets.UTF_8));

            Res created = createDiary(member, roomId);
            assertEquals(200, created.status(), String.valueOf(created.body()));
            assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.columns"
                    + " where table_name = 'diary' and column_name = 'visibility'", Integer.class));
            assertEquals("NO", jdbc.queryForObject("select is_nullable from information_schema.columns"
                    + " where table_name = 'diary' and column_name = 'room_id'", String.class));
        } finally {
            jdbc.execute("alter table diary drop column if exists visibility");
            jdbc.execute("alter table diary alter column room_id set not null");
        }
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
        assertEquals(2, count("user_learning_signal"));  // 일기(member) + 댓글(owner)
        assertEquals(200, call(HttpMethod.DELETE, "/api/diaries/" + diaryId, member, null).status());
        assertEquals(0, count("diary"));
        assertEquals(0, count("diary_reaction"));
        assertEquals(0, count("diary_comment"));
        assertEquals(0, count("user_learning_signal"));
    }

    @Test
    void 일기와_댓글은_학습_신호로_남고_댓글을_지우면_신호도_지워진다() {
        long roomId = createRoomWithMember();
        long diaryId = createDiary(member, roomId).data().get("diaryId").asLong();
        long commentId = call(HttpMethod.POST, "/api/diaries/" + diaryId + "/comments", owner, Map.of("content", "댓글"))
                .data().get("id").asLong();

        assertEquals(1, signalCount("DIARY", diaryId));
        assertEquals(1, signalCount("COMMENT", commentId));

        assertEquals(200, call(HttpMethod.DELETE, "/api/diaries/" + diaryId + "/comments/" + commentId, owner, null).status());
        assertEquals(0, signalCount("COMMENT", commentId));
        assertEquals(1, signalCount("DIARY", diaryId));
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

    // ------------------------------------------------------------------ 댓글

    @Test
    void 댓글_응답에_작성자_정보와_내_댓글_여부가_있다() {
        long roomId = createRoomWithMember();
        long diaryId = createDiary(member, roomId).data().get("diaryId").asLong();

        Res created = call(HttpMethod.POST, "/api/diaries/" + diaryId + "/comments", owner, Map.of("content", "댓글"));
        assertEquals(200, created.status(), String.valueOf(created.body()));
        assertEquals("방장", created.data().get("authorNickname").asText());
        assertEquals("salt_bread", created.data().get("authorBreadType").asText());
        assertTrue(created.data().get("isMine").asBoolean());
        long commentId = created.data().get("id").asLong();

        Res updated = call(HttpMethod.PUT, "/api/diaries/" + diaryId + "/comments/" + commentId, owner,
                Map.of("content", "수정"));
        assertEquals("방장", updated.data().get("authorNickname").asText());
        assertTrue(updated.data().get("isMine").asBoolean());

        call(HttpMethod.POST, "/api/diaries/" + diaryId + "/comments", member, Map.of("content", "답글"));
        JsonNode list = call(HttpMethod.GET, "/api/diaries/" + diaryId + "/comments", member, null).data();
        assertEquals(2, list.size());
        assertEquals("방장", list.get(0).get("authorNickname").asText());
        assertFalse(list.get(0).get("isMine").asBoolean());
        assertEquals(owner.id(), list.get(0).get("userId").asLong());
        assertEquals("멤버", list.get(1).get("authorNickname").asText());
        assertTrue(list.get(1).get("authorBreadType").isNull());  // 설문 전
        assertTrue(list.get(1).get("isMine").asBoolean());
    }

    // ------------------------------------------------------------------ 달력

    @Test
    void 달력은_한국_시간_기준으로_그달_내_일기를_날짜별로_묶는다() {
        long roomA = createRoomWithMember();
        long roomB = createRoom(member, "방B");
        // UTC 9/30 15:30 = KST 10/1 00:30 → 10월 1일
        long firstDay = diaryAt(member, roomA, "2026-09-30T15:30:00Z");
        // KST 10/15에 두 개(다른 방 포함) → 하루 항목 하나에 작성순
        long later = diaryAt(member, roomB, "2026-10-15T09:00:00Z");
        long earlier = diaryAt(member, roomA, "2026-10-15T01:00:00Z");
        // UTC 9/30 14:59 = KST 9/30 23:59, UTC 10/31 15:00 = KST 11/1 00:00 → 10월이 아니다
        diaryAt(member, roomA, "2026-09-30T14:59:00Z");
        diaryAt(member, roomA, "2026-10-31T15:00:00Z");
        // 다른 사람 일기는 빠진다
        diaryAt(owner, roomA, "2026-10-20T03:00:00Z");

        Res res = call(HttpMethod.GET, "/api/diaries/calendar?year=2026&month=10", member, null);
        assertEquals(200, res.status(), String.valueOf(res.body()));
        JsonNode days = res.data();
        assertEquals(2, days.size(), days.toString());
        assertEquals("2026-10-01", days.get(0).get("date").asText());
        assertEquals(List.of(firstDay), longs(days.get(0).get("diaryIds")));
        assertEquals("2026-10-15", days.get(1).get("date").asText());
        assertEquals(List.of(earlier, later), longs(days.get(1).get("diaryIds")));
        assertFalse(days.get(0).has("content"));

        assertEquals(0, call(HttpMethod.GET, "/api/diaries/calendar?year=2026&month=12", member, null).data().size());
        assertEquals(400, call(HttpMethod.GET, "/api/diaries/calendar?year=2026&month=13", member, null).status());
        assertEquals(400, call(HttpMethod.GET, "/api/diaries/calendar?year=2026", member, null).status());
    }

    // ------------------------------------------------------------------ 공유방

    @Test
    void 방을_만들면_방장이_OWNER로_등록되고_내_방_목록은_최근_것이_먼저다() {
        long first = createRoom(owner, "첫번째");
        long second = createRoom(owner, "두번째");

        assertEquals("OWNER", jdbc.queryForObject(
                "select role from diary_room_member where room_id = ? and user_id = ?", String.class, first, owner.id()));
        assertEquals(200, call(HttpMethod.POST, "/api/diary-rooms/join", member,
                Map.of("inviteCode", inviteCode(owner, first), "nickname", "멤버")).status());
        JsonNode rooms = call(HttpMethod.GET, "/api/diary-rooms", owner, null).data();
        assertEquals(second, rooms.get(0).get("id").asLong());
        assertEquals(first, rooms.get(1).get("id").asLong());
        assertEquals(1, rooms.get(0).get("memberCount").asLong());
        assertEquals(2, rooms.get(1).get("memberCount").asLong());
        assertEquals(6, rooms.get(1).get("maxMemberCount").asInt());
    }

    @Test
    void 방_수정은_방장만_가능하고_설명도_반영된다() {
        long roomId = createRoomWithMember();

        assertEquals(403, call(HttpMethod.PUT, "/api/diary-rooms/" + roomId, member, updateBody("x", 4, "멤버")).status());
        Res updated = call(HttpMethod.PUT, "/api/diary-rooms/" + roomId, owner,
                Map.of("name", "새이름", "description", "새설명", "maxMemberCount", 3, "ownerNickname", " 새방장 "));
        assertEquals(200, updated.status());
        assertEquals("새이름", updated.data().get("name").asText());
        assertEquals("새설명", updated.data().get("description").asText());
        assertEquals(3, updated.data().get("maxMemberCount").asInt());
        assertEquals(2, updated.data().get("memberCount").asLong());
        assertEquals("새설명", jdbc.queryForObject("select description from diary_room where id = ?", String.class, roomId));
        assertEquals("새방장", nicknameOf(roomId, owner));
    }

    @Test
    void 방_수정에서_최대_인원을_현재_인원보다_줄이거나_닉네임이_겹치면_409() {
        long roomId = createRoomWithMember();
        String nicknameOfMember = nicknameOf(roomId, member);

        Res tooSmall = call(HttpMethod.PUT, "/api/diary-rooms/" + roomId, owner, updateBody("방", 1, "방장"));
        assertEquals(400, tooSmall.status());
        // 3명 방에 3명이 있을 때 2로 줄이면 409
        String code = inviteCode(owner, roomId);
        assertEquals(200, call(HttpMethod.POST, "/api/diary-rooms/join", outsider, Map.of("inviteCode", code, "nickname", "외부인")).status());
        Res below = call(HttpMethod.PUT, "/api/diary-rooms/" + roomId, owner, updateBody("방", 2, "방장"));
        assertEquals(409, below.status());
        assertEquals("DIARY_ROOM_MAX_BELOW_MEMBER_COUNT", below.body().path("code").asText());

        Res dup = call(HttpMethod.PUT, "/api/diary-rooms/" + roomId, owner, updateBody("방", 6, nicknameOfMember));
        assertEquals(409, dup.status());
        assertEquals("DIARY_ROOM_NICKNAME_DUPLICATED", dup.body().path("code").asText());
        // 실패한 수정은 아무것도 바꾸지 않는다
        assertEquals(6, jdbc.queryForObject("select max_member_count from diary_room where id = ?", Integer.class, roomId));
    }

    @Test
    void 방을_만들_때_최대_인원은_2에서_6명이고_닉네임은_필수다() {
        String rooms = "/api/diary-rooms";
        assertEquals(400, call(HttpMethod.POST, rooms, owner, Map.of("name", "방", "maxMemberCount", 1, "ownerNickname", "방장")).status());
        assertEquals(400, call(HttpMethod.POST, rooms, owner, Map.of("name", "방", "maxMemberCount", 7, "ownerNickname", "방장")).status());
        assertEquals(400, call(HttpMethod.POST, rooms, owner, Map.of("name", "방", "ownerNickname", "방장")).status());
        assertEquals(400, call(HttpMethod.POST, rooms, owner, Map.of("name", "방", "maxMemberCount", 4)).status());
        assertEquals(400, call(HttpMethod.POST, rooms, owner, Map.of("name", "방", "maxMemberCount", 4, "ownerNickname", " ")).status());
        assertEquals(400, call(HttpMethod.POST, rooms, owner, Map.of("name", "방", "maxMemberCount", 4, "ownerNickname", "열한글자닉네임입니다요")).status());

        Res created = call(HttpMethod.POST, rooms, owner, Map.of("name", "방", "maxMemberCount", 2, "ownerNickname", "방장닉"));
        assertEquals(200, created.status());
        assertEquals(2, created.data().get("maxMemberCount").asInt());
        assertEquals(1, created.data().get("memberCount").asLong());
        assertEquals("방장닉", nicknameOf(created.data().get("id").asLong(), owner));
    }

    @Test
    void 방_단건_조회는_방장과_멤버만_가능하고_인원_정보를_준다() {
        long roomId = createRoomWithMember();

        Res detail = call(HttpMethod.GET, "/api/diary-rooms/" + roomId, member, null);
        assertEquals(200, detail.status());
        assertEquals(roomId, detail.data().get("id").asLong());
        assertEquals(owner.id(), detail.data().get("ownerId").asLong());
        assertEquals(6, detail.data().get("maxMemberCount").asInt());
        assertEquals(2, detail.data().get("memberCount").asLong());

        assertEquals(200, call(HttpMethod.GET, "/api/diary-rooms/" + roomId, owner, null).status());
        assertEquals(403, call(HttpMethod.GET, "/api/diary-rooms/" + roomId, outsider, null).status());
        assertEquals(404, call(HttpMethod.GET, "/api/diary-rooms/999999", owner, null).status());
    }

    @Test
    void 방을_삭제하면_방의_일기_반응_댓글_그룹_질문_학습_신호_멤버가_함께_지워지고_다른_방은_남는다() {
        long roomA = createRoomWithMember();
        long roomB = createRoom(owner, "남는방");
        long diaryId = createDiary(member, roomA).data().get("diaryId").asLong();
        long keptDiaryId = createDiary(owner, roomB).data().get("diaryId").asLong();
        react(owner, diaryId, "😊");
        call(HttpMethod.POST, "/api/diaries/" + diaryId + "/comments", owner, Map.of("content", "댓글"));
        long answerA = insertRoomAnswer(roomA, member);
        long answerB = insertRoomAnswer(roomB, owner);

        assertEquals(403, call(HttpMethod.DELETE, "/api/diary-rooms/" + roomA, member, null).status());
        assertEquals(200, call(HttpMethod.DELETE, "/api/diary-rooms/" + roomA, owner, null).status());

        assertEquals(1, count("diary_room"));
        assertEquals(1, count("diary"));
        assertEquals(0, count("diary_reaction"));
        assertEquals(0, count("diary_comment"));
        assertEquals(1, count("diary_room_member"));
        assertEquals(1, count("room_question"));
        assertEquals(1, count("room_question_answer"));
        // 남는 방의 일기·답 신호만 남는다
        assertEquals(2, count("user_learning_signal"));
        assertEquals(1, signalCount("DIARY", keptDiaryId));
        assertEquals(1, signalCount("ROOM_ANSWER", answerB));
        assertEquals(0, signalCount("ROOM_ANSWER", answerA));
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

        JsonNode memberList = call(HttpMethod.GET, members, member, null).data();
        assertEquals(2, memberList.size());
        assertEquals("OWNER", memberList.get(0).get("role").asText());
        assertEquals("방장", memberList.get(0).get("nickname").asText());
        assertEquals("멤버", memberList.get(1).get("nickname").asText());
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
    void 탈퇴하면_내_학습_신호와_학습_결과가_지워지고_다른_사람_것은_남는다() {
        long roomId = createRoomWithMember();
        long diaryId = createDiary(member, roomId).data().get("diaryId").asLong();
        call(HttpMethod.POST, "/api/diaries/" + diaryId + "/comments", member, Map.of("content", "댓글"));
        long ownerDiaryId = createDiary(owner, roomId).data().get("diaryId").asLong();
        jdbc.update("insert into user_speech_style (user_id, style_json, update_count, updated_at) "
                + "values (?, '{}'::jsonb, 1, now())", member.id());
        jdbc.update("insert into user_personality_adjustment (user_id, deltas_json, update_count, updated_at) "
                + "values (?, '{}'::jsonb, 1, now())", member.id());

        assertEquals(200, call(HttpMethod.DELETE, "/api/users/me", member, null).status());

        assertEquals(0, jdbc.queryForObject(
                "select count(*) from user_learning_signal where user_id = ?", Integer.class, member.id()));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from user_speech_style where user_id = ?", Integer.class, member.id()));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from user_personality_adjustment where user_id = ?", Integer.class, member.id()));
        assertEquals(1, signalCount("DIARY", ownerDiaryId));
    }

    @Test
    void 방장이_직접_추가할_때_방_닉네임을_정할_수_있고_겹치면_409() {
        long roomId = createRoom(owner, "방");
        String members = "/api/diary-rooms/" + roomId + "/members";

        Res dup = call(HttpMethod.POST, members, owner, Map.of("userId", member.id(), "nickname", " 방장 "));
        assertEquals(409, dup.status());
        assertEquals("DIARY_ROOM_NICKNAME_DUPLICATED", dup.body().path("code").asText());
        assertEquals(400, call(HttpMethod.POST, members, owner,
                Map.of("userId", member.id(), "nickname", "열한글자닉네임입니다요")).status());

        assertEquals(200, call(HttpMethod.POST, members, owner, Map.of("userId", member.id(), "nickname", " 단짝 ")).status());
        assertEquals("단짝", nicknameOf(roomId, member));

        // 계정 닉네임이 이미 방에 있어도 다른 닉네임을 정해 주면 들어올 수 있다
        TestUser sameName = createUser("방장", null);
        assertEquals(409, call(HttpMethod.POST, members, owner, Map.of("userId", sameName.id())).status());
        assertEquals(200, call(HttpMethod.POST, members, owner, Map.of("userId", sameName.id(), "nickname", "방장2")).status());
        assertEquals("방장2", nicknameOf(roomId, sameName));
    }

    @Test
    void 방_닉네임은_DB에서도_같은_방_안에서_UNIQUE다() {
        Integer uniqueConstraints = jdbc.queryForObject(
                "select count(*) from pg_constraint where conname = 'uk_diary_room_member_room_nickname' and contype = 'u'",
                Integer.class);
        assertEquals(1, uniqueConstraints);
    }

    // ------------------------------------------------------------------ 초대 코드

    @Test
    void 방을_만들면_8자리_초대_코드가_생기고_방장과_멤버만_조회할_수_있다() {
        long roomId = createRoomWithMember();

        Res ownerView = call(HttpMethod.GET, "/api/diary-rooms/" + roomId + "/invite-code", owner, null);
        assertEquals(200, ownerView.status());
        String code = ownerView.data().get("inviteCode").asText();
        assertTrue(code.matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}"), "code=" + code);

        assertEquals(code, call(HttpMethod.GET, "/api/diary-rooms/" + roomId + "/invite-code", member, null)
                .data().get("inviteCode").asText());
        assertEquals(403, call(HttpMethod.GET, "/api/diary-rooms/" + roomId + "/invite-code", outsider, null).status());
    }

    @Test
    void 초대_코드로_누구나_참여할_수_있고_대소문자를_구분하지_않으며_다시_참여해도_200() {
        long roomId = createRoom(owner, "방");
        String code = inviteCode(owner, roomId);

        Res joined = call(HttpMethod.POST, "/api/diary-rooms/join", outsider, Map.of("inviteCode", " " + code.toLowerCase() + " ", "nickname", "참여자"));
        assertEquals(200, joined.status());
        assertEquals(roomId, joined.data().get("roomId").asLong());
        assertEquals(outsider.id(), joined.data().get("userId").asLong());
        assertTrue(joined.data().get("isMember").asBoolean());
        assertEquals(2, joined.data().get("memberCount").asLong());
        assertEquals("MEMBER", jdbc.queryForObject(
                "select role from diary_room_member where room_id = ? and user_id = ?", String.class, roomId, outsider.id()));

        Res again = call(HttpMethod.POST, "/api/diary-rooms/join", outsider, Map.of("inviteCode", code, "nickname", "참여자"));
        assertEquals(200, again.status());
        assertEquals(2, again.data().get("memberCount").asLong());

        // 방장이 자기 방 코드로 참여해도 역할은 OWNER 그대로
        assertEquals(200, call(HttpMethod.POST, "/api/diary-rooms/join", owner, Map.of("inviteCode", code, "nickname", "참여자")).status());
        assertEquals("OWNER", jdbc.queryForObject(
                "select role from diary_room_member where room_id = ? and user_id = ?", String.class, roomId, owner.id()));

        // 참여하면 방 피드를 볼 수 있다
        assertEquals(200, call(HttpMethod.GET, "/api/diary-rooms/" + roomId + "/diaries", outsider, null).status());
    }

    @Test
    void 초대_코드_참여는_최대_인원까지만_되고_꽉_차면_409() {
        long roomId = createRoom(owner, "둘방", 2);
        String code = inviteCode(owner, roomId);

        assertEquals(200, call(HttpMethod.POST, "/api/diary-rooms/join", member, Map.of("inviteCode", code, "nickname", "멤버")).status());
        Res full = call(HttpMethod.POST, "/api/diary-rooms/join", outsider, Map.of("inviteCode", code, "nickname", "외부인"));
        assertEquals(409, full.status());
        assertEquals("DIARY_ROOM_FULL", full.body().path("code").asText());
        // 이미 멤버면 꽉 찬 방이어도 200
        assertEquals(200, call(HttpMethod.POST, "/api/diary-rooms/join", member, Map.of("inviteCode", code, "nickname", "멤버")).status());
        // 방장이 직접 추가해도 꽉 찼으면 409
        assertEquals(409, call(HttpMethod.POST, "/api/diary-rooms/" + roomId + "/members", owner, Map.of("userId", outsider.id())).status());
        assertEquals(2, count("diary_room_member"));
    }

    @Test
    void 같은_방에_같은_닉네임으로는_참여할_수_없고_다른_방이면_된다() {
        long roomA = createRoom(owner, "A");
        long roomB = createRoom(owner, "B");

        Res dup = call(HttpMethod.POST, "/api/diary-rooms/join", member, Map.of("inviteCode", inviteCode(owner, roomA), "nickname", " 방장 "));
        assertEquals(409, dup.status());
        assertEquals("DIARY_ROOM_NICKNAME_DUPLICATED", dup.body().path("code").asText());
        assertEquals(400, call(HttpMethod.POST, "/api/diary-rooms/join", member, Map.of("inviteCode", inviteCode(owner, roomA))).status());

        assertEquals(200, call(HttpMethod.POST, "/api/diary-rooms/join", member, Map.of("inviteCode", inviteCode(owner, roomA), "nickname", " 새싹 ")).status());
        assertEquals("새싹", nicknameOf(roomA, member));
        Res sameInB = call(HttpMethod.POST, "/api/diary-rooms/join", outsider, Map.of("inviteCode", inviteCode(owner, roomB), "nickname", "새싹"));
        assertEquals(200, sameInB.status());
    }

    @Test
    void 마지막_한_자리에_동시에_참여해도_최대_인원을_넘지_않는다() throws Exception {
        long roomId = createRoom(owner, "둘방", 2);
        String code = inviteCode(owner, roomId);
        List<TestUser> joiners = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            joiners.add(createUser("참여" + i, null));
        }
        ExecutorService pool = Executors.newFixedThreadPool(joiners.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < joiners.size(); i++) {
            TestUser joiner = joiners.get(i);
            String nickname = "참여" + i;
            pool.submit(() -> {
                start.await();
                statuses.add(call(HttpMethod.POST, "/api/diary-rooms/join", joiner, Map.of("inviteCode", code, "nickname", nickname)).status());
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, statuses.stream().filter(s -> s == 200).count(), statuses.toString());
        assertEquals(joiners.size() - 1, statuses.stream().filter(s -> s == 409).count(), statuses.toString());
        assertEquals(2, count("diary_room_member"));
    }

    @Test
    void 없는_초대_코드는_404_빈_코드는_400() {
        createRoom(owner, "방");

        Res wrong = call(HttpMethod.POST, "/api/diary-rooms/join", member, Map.of("inviteCode", "ZZZZZZZZ", "nickname", "참여자"));
        assertEquals(404, wrong.status());
        assertEquals("초대 코드가 올바르지 않습니다. 다시 확인해주세요.", wrong.message());
        assertEquals(400, call(HttpMethod.POST, "/api/diary-rooms/join", member, Map.of("inviteCode", " ", "nickname", "참여자")).status());
    }

    @Test
    void 초대_코드_재발급은_방장만_가능하고_이전_코드는_쓸_수_없다() {
        long roomId = createRoomWithMember();
        String oldCode = inviteCode(owner, roomId);

        assertEquals(403, call(HttpMethod.POST, "/api/diary-rooms/" + roomId + "/invite-code", member, null).status());
        Res regenerated = call(HttpMethod.POST, "/api/diary-rooms/" + roomId + "/invite-code", owner, null);
        assertEquals(200, regenerated.status());
        String newCode = regenerated.data().get("inviteCode").asText();
        assertFalse(newCode.equals(oldCode));

        assertEquals(404, call(HttpMethod.POST, "/api/diary-rooms/join", outsider, Map.of("inviteCode", oldCode, "nickname", "참여자")).status());
        assertEquals(200, call(HttpMethod.POST, "/api/diary-rooms/join", outsider, Map.of("inviteCode", newCode, "nickname", "참여자")).status());
    }

    @Test
    void 강퇴하면_초대_코드가_바뀌어_강퇴된_사람은_이전_코드로_다시_못_들어온다() {
        long roomId = createRoom(owner, "방");
        String oldCode = inviteCode(owner, roomId);
        assertEquals(200, call(HttpMethod.POST, "/api/diary-rooms/join", member, Map.of("inviteCode", oldCode, "nickname", "참여자")).status());

        assertEquals(200, call(HttpMethod.DELETE, "/api/diary-rooms/" + roomId + "/members/kick?userId=" + member.id(), owner, null).status());

        String newCode = inviteCode(owner, roomId);
        assertFalse(newCode.equals(oldCode));
        assertEquals(404, call(HttpMethod.POST, "/api/diary-rooms/join", member, Map.of("inviteCode", oldCode, "nickname", "참여자")).status());
        assertFalse(jdbc.queryForObject(
                "select count(*) > 0 from diary_room_member where room_id = ? and user_id = ?", Boolean.class, roomId, member.id()));
    }

    @Test
    void 멤버가_아닌_사람을_강퇴하면_초대_코드는_그대로다() {
        long roomId = createRoom(owner, "방");
        String code = inviteCode(owner, roomId);

        assertEquals(200, call(HttpMethod.DELETE, "/api/diary-rooms/" + roomId + "/members/kick?userId=" + outsider.id(), owner, null).status());
        assertEquals(code, inviteCode(owner, roomId));
    }

    @Test
    void 초대_코드는_DB에서_NOT_NULL_UNIQUE다() {
        assertEquals("NO", jdbc.queryForObject(
                "select is_nullable from information_schema.columns where table_name = 'diary_room' and column_name = 'invite_code'",
                String.class));
        Integer uniqueConstraints = jdbc.queryForObject(
                "select count(*) from pg_constraint con join pg_class rel on rel.oid = con.conrelid "
                        + "where rel.relname = 'diary_room' and con.contype = 'u' "
                        + "and con.conkey = array[(select attnum from pg_attribute where attrelid = rel.oid and attname = 'invite_code')]",
                Integer.class);
        assertEquals(1, uniqueConstraints);
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
        return createRoom(who, name, 6);
    }

    private long createRoom(TestUser who, String name, int maxMemberCount) {
        Res res = call(HttpMethod.POST, "/api/diary-rooms", who,
                Map.of("name", name, "maxMemberCount", maxMemberCount, "ownerNickname", "방장"));
        assertEquals(200, res.status(), String.valueOf(res.body()));
        return res.data().get("id").asLong();
    }

    private Map<String, Object> updateBody(String name, int maxMemberCount, String ownerNickname) {
        return Map.of("name", name, "maxMemberCount", maxMemberCount, "ownerNickname", ownerNickname);
    }

    private String nicknameOf(long roomId, TestUser who) {
        return jdbc.queryForObject("select nickname from diary_room_member where room_id = ? and user_id = ?",
                String.class, roomId, who.id());
    }

    /** owner가 방을 만들고 member를 초대한다. */
    private long createRoomWithMember() {
        long roomId = createRoom(owner, "방");
        assertEquals(200, call(HttpMethod.POST, "/api/diary-rooms/" + roomId + "/members", owner,
                Map.of("userId", member.id())).status());
        return roomId;
    }

    private String inviteCode(TestUser who, long roomId) {
        Res res = call(HttpMethod.GET, "/api/diary-rooms/" + roomId + "/invite-code", who, null);
        assertEquals(200, res.status(), String.valueOf(res.body()));
        return res.data().get("inviteCode").asText();
    }

    private Res createDiary(TestUser who, long roomId) {
        return call(HttpMethod.POST, "/api/diaries", who, Map.of("roomId", roomId, "title", "제목", "content", "내용"));
    }

    /** 일기를 쓰고 작성 시각을 utcInstant로 바꾼다. */
    private long diaryAt(TestUser who, long roomId, String utcInstant) {
        long diaryId = createDiary(who, roomId).data().get("diaryId").asLong();
        jdbc.update("update diary set created_at = ?::timestamptz where id = ?", utcInstant, diaryId);
        return diaryId;
    }

    private static List<Long> longs(JsonNode array) {
        List<Long> values = new ArrayList<>();
        array.forEach(n -> values.add(n.asLong()));
        return values;
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

    private int signalCount(String sourceType, long sourceId) {
        return jdbc.queryForObject("select count(*) from user_learning_signal where source_type = ? and source_id = ?",
                Integer.class, sourceType, sourceId);
    }

    /** 방에 오늘 그룹 질문(ready)과 who의 답, 그 답의 학습 신호를 직접 넣는다. AI 호출 없이 삭제 연쇄만 보려는 것. @return 답 id */
    private long insertRoomAnswer(long roomId, TestUser who) {
        Long questionId = jdbc.queryForObject(
                "insert into room_question (room_id, question_date, content, status, requested_by, created_at, updated_at) "
                        + "values (?, current_date, '질문', 'READY', ?, now(), now()) returning id",
                Long.class, roomId, who.id());
        Long answerId = jdbc.queryForObject(
                "insert into room_question_answer (question_id, user_id, content, created_at, updated_at) "
                        + "values (?, ?, '답', now(), now()) returning id",
                Long.class, questionId, who.id());
        jdbc.update("insert into user_learning_signal (user_id, source_type, source_id, created_at) "
                + "values (?, 'ROOM_ANSWER', ?, now())", who.id(), answerId);
        return answerId;
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
}

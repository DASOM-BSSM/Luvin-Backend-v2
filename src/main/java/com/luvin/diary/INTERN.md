# diary — 감정일기 (인턴 ver)

일기 · 공유방 · 댓글 · 공감 · 그룹 질문 기능 모듈. 기간: 9/18(금) ~ 9/30(수) (그룹 질문·학습 연동은 이후 추가)

## 패키지 구조

```
diary/
├── controller/  DiaryController, DiaryRoomController, DiaryCommentController, RoomQuestionController
├── service/     DiaryService, DiaryRoomService, DiaryCommentService, RoomQuestionService
├── repository/  DiaryRepository, DiaryRoomRepository, DiaryCommentRepository,
│                RoomQuestionRepository, RoomQuestionAnswerRepository
├── domain/      Diary, DiaryReaction, Emoji(반응 이모지 검사),
│                DiaryRoom, DiaryRoomMember, DiaryRoomMemberRole(enum), DiaryComment,
│                RoomQuestion, RoomQuestionAnswer, RoomQuestionStatus(enum)
└── dto/         DiaryDto, DiaryRoomDto, DiaryCommentDto, RoomQuestionDto
```

- controller / service / repository / dto는 일기 · 공유방 · 댓글 · 그룹 질문 4개 단위로 맞춘다.
- 서비스는 인터페이스+Impl로 나누지 않고 `@Service` 클래스 하나로 둔다.
- 도메인(엔티티)은 테이블 단위라서 합칠 수 없다(아래 ERD의 테이블 7개).
  - `DiaryReaction`, `DiaryRoomMember`는 ERD 규칙대로 id 없이 복합 PK를 쓴다
    (엔티티 안의 `@EmbeddedId Pk` 클래스).
  - 이 둘은 레포지토리를 따로 만들지 않는다. `DiaryReaction`은 `DiaryRepository`에서, `DiaryRoomMember`는
    `DiaryRoomRepository`에서 연관관계(cascade)나 `@Query`로 같이 다룬다.
- DTO는 도메인당 파일 하나다. 요청/응답은 그 안의 중첩 클래스로 둔다
  (`DiaryDto.CreateRequest`, `DiaryDto.FeedItem`, `DiaryRoomDto.MemberResponse` 등).
  springdoc이 중첩 클래스 이름(Request, Response …)끼리 덮어쓰므로 모두 `@Schema(name = "DiaryRoom…")`처럼 고유한 이름을 붙인다.

## 확정된 설계 (구현 중 변경된 것)

- **공개범위 없음. 일기는 항상 공유방 하나 안에서 쓴다.** 볼 수 있는 사람은 작성자 + 그 방의 방장·멤버(`Diary.canBeViewedBy`).
  작성 시 `roomId` 필수, 수정은 제목·내용만(방 이동 불가).
- **공유방 인원 제한.** 방을 만들 때 최대 인원 `maxMemberCount`(방장 포함 2~6명)를 정한다. 꽉 차면 참여·직접 추가 모두 409 `DIARY_ROOM_FULL`.
  방 수정에서 현재 인원보다 적게 줄이면 409 `DIARY_ROOM_MAX_BELOW_MEMBER_COUNT`.
  "인원·닉네임 확인 → 추가"는 방 행을 `PESSIMISTIC_WRITE`로 잠그고 해서(`findByIdForUpdate`, `findByInviteCodeForUpdate`) 동시 참여에도 넘치지 않는다.
- **방별 닉네임.** 멤버마다 이 방에서만 쓰는 닉네임(앞뒤 공백 제외 1~10자)이 있고, **같은 방 안에서는 겹칠 수 없다.**
  - 방 생성·수정: `ownerNickname`(필수)이 방장 닉네임.
  - 초대 코드 참여: `nickname`(필수).
  - 방장 직접 추가: `nickname`(선택). 비우면 계정 닉네임(없으면 이름, 10자까지).
  - 겹치면 409 `DIARY_ROOM_NICKNAME_DUPLICATED`. 서비스가 잠근 상태에서 먼저 확인하고(`existsNickname`),
    DB에도 `UNIQUE(room_id, nickname)`(`uk_diary_room_member_room_nickname`)가 걸려 있다. 비교는 공백 제거 후 정확히 같은지(대소문자 구분).
  - 이미 멤버인 사람이 다시 참여·추가되면 200이고 닉네임은 바뀌지 않는다.
- **공유방 참여는 초대 코드.** 방마다 8자리 영구 코드(헷갈리는 0/O, 1/I/L 제외)가 있고, 코드를 아는 사람은 최대 인원까지 참여한다.
  방장·멤버가 코드를 조회해 공유하고, 유출되면 방장이 재발급한다(이전 코드 즉시 무효). 방장이 userId로 직접 추가하는 API도 남아 있다.
  **멤버를 강퇴하면 코드가 자동으로 재발급**된다(강퇴된 사람이 같은 코드로 바로 재참여하는 것을 막음). 이전 코드는 모두에게 무효가 되므로 새 코드를 다시 공유해야 한다.
  코드는 Flyway `V7__diary_room_invite_code.sql`이 기존 방에 채우고 NOT NULL·UNIQUE를 건다.
  방장은 나갈 수 없다(400).
  방장 확인은 `diary_room.owner_id` 하나로만 한다. 방을 만들면 방장이 `OWNER`로 멤버 테이블에도 들어간다.
- **그룹 질문.** 방 멤버 누구나 "질문 생성하기"로 AI 질문을 만든다. 방당 하루(KST) 1개(`UNIQUE(room_id, question_date)`).
  생성은 비동기: POST는 바로 202 + `status: generating`, AI 응답이 오면 `ready`(실패하면 `failed`). 오래 멈춘 generating은 failed로 보이고 다시 생성할 수 있다.
  AI에는 방 이름·멤버 수·최근 질문만 넘긴다(일기 본문은 안 넘김). 답은 한 사람당 질문 하나에 1개(다시 보내면 수정, 1~200자).
- **학습 신호.** 내가 쓴 일기·댓글·그룹 질문 답은 쓸 때 `user_learning_signal`에 신호로 남는다(`LearningSignalService.record`, 학습 동의가 꺼져 있으면 안 남김).
  원본을 지우면 신호도 같이 지운다: 댓글 삭제 → `remove`, 일기 삭제 → `removeForDiary`(일기 + 그 댓글), 방 삭제 → `removeForRoom`(방의 일기·댓글·답).
  학습 신호 삭제 쿼리가 원본 테이블을 조인해서 찾으므로 **원본보다 먼저** 지운다.
- **공감 = 이모지 반응.** 디자인 기본 6개(❤️😊😭😂🥺😍) + 이모지 키보드의 아무 이모지나 가능, 문자 그대로 저장한다.
  한 사람은 일기 하나에 반응 1개: 같은 이모지 다시 누르면 취소, 다른 이모지면 교체. 이모지 1개가 아니면 400.
  ❤️처럼 뒤에 붙는 표시 문자(U+FE0F)는 빼고 저장해서 ❤ / ❤️ 를 같은 반응으로 본다(`Emoji.normalize`).
- **커뮤니티(`/api/diaries/community`) = 내가 속한 모든 방의 일기 모아보기**(최신 50개).
- 피드 항목에는 작성자 닉네임(없으면 이름)과 빵 타입 id(`users.personality_type`, 예: `salt_bread`)가 들어간다.

## ERD (팀 ERD 규칙 + 기존 DB 기준)

팀 ERD 규칙: PostgreSQL 18, 단수형 테이블 이름, `id bigint GENERATED ALWAYS AS IDENTITY`,
`timestamptz`, 연결 테이블은 복합 PK, 문자열 길이는 백엔드에서 관리.

**기존 DB에서 돌린다.** 팀 ERD는 `user.id uuid`지만 지금 DB는 `users.user_id bigint`라서, diary의 사용자 컬럼
(`user_id`, `owner_id`)은 `bigint`로 두고 `users.user_id`를 가리킨다. 값은 `SecurityUtils.getCurrentUserId()`(Long) 그대로 쓴다.

일기와 공유방은 **일기 N개 → 공유방 1개**다(`diary.room_id`, not null). 일기는 항상 방 안에만 있다.

```
table diary [note: '감정일기'] {
  id bigint [pk, increment]
  user_id bigint [not null]
  room_id bigint [not null]
  title text [not null]
  content text [not null]
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
}

table diary_reaction [note: '일기 이모지 반응'] {
  diary_id bigint [not null]
  user_id bigint [not null]
  emoji varchar(32) [not null]          // 이모지 문자 그대로 (U+FE0F 제거)
  created_at timestamptz [not null]
  indexes { (diary_id, user_id) [pk] }   // 한 사람당 일기 하나에 반응 1개
}

enum room_member_role {
  owner
  member
}

table diary_room [note: '공유방'] {
  id bigint [pk, increment]
  owner_id bigint [not null]
  name text [not null]
  description text
  invite_code varchar(8) [not null, unique]   // 영구 초대 코드 (강퇴·재발급 때만 바뀜). 기존 방은 Flyway V7이 채움
  max_member_count integer [not null]         // 방장 포함 2~6명. 기존 방은 Flyway V13이 6으로 채움
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
}

table diary_room_member [note: '공유방 멤버'] {
  room_id bigint [not null]
  user_id bigint [not null]
  role room_member_role [not null, default: 'member']
  nickname text [not null]                    // 방별 닉네임 1~10자. 기존 멤버는 Flyway V13이 채우고 겹치면 숫자를 붙임
  joined_at timestamptz [not null]
  indexes {
    (room_id, user_id) [pk]
    (room_id, nickname) [unique]              // uk_diary_room_member_room_nickname
  }
}

table room_question [note: '그룹 질문 (Flyway V12)'] {
  id bigint [pk, increment]
  room_id bigint [not null]
  question_date date [not null]               // KST
  content varchar(300)                        // READY일 때만
  status varchar(20) [not null]               // GENERATING | READY | FAILED
  requested_by bigint [not null]
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
  indexes { (room_id, question_date) [unique] }
}

table room_question_answer [note: '그룹 질문 답 (Flyway V12)'] {
  id bigint [pk, increment]
  question_id bigint [not null]
  user_id bigint [not null]
  content varchar(200) [not null]
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
  indexes { (question_id, user_id) [unique] }
}

table diary_comment [note: '일기 댓글'] {
  id bigint [pk, increment]
  diary_id bigint [not null]
  user_id bigint [not null]
  content text [not null]
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
}

Ref: diary.user_id > users.user_id [delete: restrict]
Ref: diary.room_id > diary_room.id [delete: cascade]
Ref: diary_reaction.diary_id > diary.id [delete: cascade]
Ref: diary_reaction.user_id > users.user_id [delete: restrict]
Ref: diary_room.owner_id > users.user_id [delete: restrict]
Ref: diary_room_member.room_id > diary_room.id [delete: cascade]
Ref: diary_room_member.user_id > users.user_id [delete: restrict]   // 실제 FK 있음 (DiaryRoomMember.member)
Ref: diary_comment.diary_id > diary.id [delete: cascade]
Ref: diary_comment.user_id > users.user_id [delete: restrict]
Ref: room_question.room_id > diary_room.id [delete: cascade]          // DB FK 없음, 서비스에서 지움
Ref: room_question_answer.question_id > room_question.id [delete: cascade]   // 실제 FK 있음 (V12, NO ACTION)
```

> - enum은 기존 모듈처럼 `@Enumerated(EnumType.STRING)`으로 저장한다. DB에는 PG enum 타입이 아니라 문자열
>   컬럼으로 들어가고, 값은 Java enum 이름(`OWNER`, `MEMBER`)이다.
> - 나중에 팀 전체가 ERD(`uuid`)로 옮기면 diary의 사용자 컬럼도 그때 같이 `uuid`로 바꾼다.

### 엔티티 매핑 상태 (DB 연결까지 완료)

- 위 ERD의 컬럼은 엔티티에 전부 매핑되어 있다. diary 테이블 5개는 `ddl-auto: update`가 만들고(임시 Postgres로 확인),
  `room_question`, `room_question_answer`는 Flyway V12가 만든다. 행은 동시 요청에도 1행만 생기도록 native `insert … on conflict`로 넣는다.
- 이미 데이터가 있는 테이블의 컬럼·제약 추가(NOT NULL, UNIQUE)는 `ddl-auto`가 안정적으로 못 하므로 Flyway가 조건부로 한다
  (V7 초대 코드, V13 인원 제한·방 닉네임). 빈 DB(CI)에서는 no-op이고 Hibernate가 엔티티 정의대로 만든다.
- 연관관계: `Diary.room`, `DiaryComment.diary`는 `@ManyToOne`이다. `DiaryReaction.diary`, `DiaryRoomMember.room`은
  `@MapsId`로 복합 PK의 일부다.
- 사용자(`user_id`, `owner_id`)는 기존 모듈처럼 `Long` 컬럼만 둔다. 예외: `DiaryRoomMember.member`는 `User`와
  연관관계라 `diary_room_member.user_id`에는 `users`로 가는 FK가 생긴다.
- ERD의 `delete: cascade / set null`은 DB FK에 반영되지 않는다(Hibernate 기본값은 NO ACTION).
  그래서 서비스에서 순서대로 먼저 지운다.
  - 댓글 삭제: 학습 신호 → 댓글
  - 일기 삭제: 학습 신호(일기·댓글) → 반응 → 댓글 → 일기
  - 공유방 삭제: 학습 신호(방의 일기·댓글·답) → 그룹 질문 답 → 그룹 질문 → 방 일기들의 반응 → 댓글 → 일기 → 멤버 → 방
  - 회원 탈퇴(`DiaryRoomService.removeUserFromAllRooms`): 방장인 방은 위 순서로 통째로 지우고, 나머지 방에서는 멤버 행만 지운다
    (`diary_room_member.user_id`가 users FK라서).
- 로컬 DB에 예전 구조(`visibility` 컬럼 등)가 남아 있으면 `ddl-auto: update`가 컬럼을 지우지 않아 저장이 실패한다.
  구조가 바뀐 뒤에는 diary 테이블 5개를 지우고 다시 띄운다.

## API 목록 (구현 완료 기준)

Swagger(`/swagger-ui`)의 "감정일기", "감정일기 공유방", "감정일기 댓글", "감정일기 그룹 질문" 태그에서도 볼 수 있다.

### 일기 — `DiaryController` (`/api/diaries`)

| 기능 | Method | Path | 비고 |
|---|---|---|---|
| 일기 작성 | POST | `/api/diaries` | `{roomId, title, content}`, 방장·멤버만, 학습 신호 기록 |
| 내 일기 목록 | GET | `/api/diaries?page=&size=` | 내가 쓴 것만, size 최대 50 |
| 일기 상세 | GET | `/api/diaries/{diaryId}` | 작성자·방장·멤버만 |
| 일기 수정 | PUT | `/api/diaries/{diaryId}` | `{title, content}`, 작성자만 |
| 일기 삭제 | DELETE | `/api/diaries/{diaryId}` | 작성자만, 반응·댓글·학습 신호도 삭제 |
| 이모지 반응 토글 | POST | `/api/diaries/{diaryId}/like` | `{emoji}` → `{diaryId, emoji, liked, likeCount}` |
| 내 방들 피드 | GET | `/api/diaries/community` | 내가 속한 방들의 일기 최신 50개 |

### 공유방 — `DiaryRoomController` (`/api/diary-rooms`)

| 기능 | Method | Path | 비고 |
|---|---|---|---|
| 공유방 생성 | POST | `/api/diary-rooms` | `{name, description, maxMemberCount(2~6), ownerNickname}`, 만든 사람이 OWNER |
| 내 공유방 목록 | GET | `/api/diary-rooms` | 최근 참여한 방이 먼저, `maxMemberCount`·`memberCount` 포함 |
| 공유방 단건 조회 | GET | `/api/diary-rooms/{roomId}` | 방장·멤버만 |
| 공유방 수정 | PUT | `/api/diary-rooms/{roomId}` | `{name, description, maxMemberCount, ownerNickname}`, 방장만, 인원·닉네임 409 |
| 공유방 삭제 | DELETE | `/api/diary-rooms/{roomId}` | 방장만, 방의 일기·반응·댓글·그룹 질문·답·학습 신호·멤버도 삭제 |
| 멤버 목록 | GET | `/api/diary-rooms/{roomId}/members` | 방장·멤버만, 방 닉네임·역할 포함, 먼저 들어온 순 |
| 멤버 추가(직접) | POST | `/api/diary-rooms/{roomId}/members` | `{userId, nickname?}`, 방장만, 꽉 참·닉네임 겹침 409 |
| 초대 코드 조회 | GET | `/api/diary-rooms/{roomId}/invite-code` | 방장·멤버만 → `{roomId, inviteCode}` |
| 초대 코드 재발급 | POST | `/api/diary-rooms/{roomId}/invite-code` | 방장만, 이전 코드 무효 |
| 초대 코드로 참여 | POST | `/api/diary-rooms/join` | `{inviteCode, nickname}`, 대소문자 무시, 이미 멤버면 200, 없는 코드 404, 꽉 참·닉네임 겹침 409 |
| 나가기 | DELETE | `/api/diary-rooms/{roomId}/members/me` | 방장은 400 |
| 강퇴 | DELETE | `/api/diary-rooms/{roomId}/members/kick?userId=` | 방장만, 자기 자신은 400, 강퇴되면 초대 코드 재발급 |
| 공유방 일기 조회 | GET | `/api/diary-rooms/{roomId}/diaries` | 방장·멤버만 |

### 댓글 — `DiaryCommentController` (`/api/diaries/{diaryId}/comments`)

| 기능 | Method | Path | 비고 |
|---|---|---|---|
| 댓글 작성 | POST | `/api/diaries/{diaryId}/comments` | 일기를 볼 수 있는 사람만, 학습 신호 기록 |
| 댓글 목록 | GET | `/api/diaries/{diaryId}/comments` | 일기를 볼 수 있는 사람만, 작성순 |
| 댓글 수정 | PUT | `/api/diaries/{diaryId}/comments/{commentId}` | 작성자만 |
| 댓글 삭제 | DELETE | `/api/diaries/{diaryId}/comments/{commentId}` | 작성자만, 학습 신호도 삭제 |

### 그룹 질문 — `RoomQuestionController` (`/api/diary-rooms/{roomId}/questions`)

공통: 없는 방 404 `ROOM_NOT_FOUND`, 방장·멤버가 아니면 403 `ROOM_FORBIDDEN`.

| 기능 | Method | Path | 비고 |
|---|---|---|---|
| 질문 생성 | POST | `/api/diary-rooms/{roomId}/questions` | 202 + `status: generating`, 오늘 질문이 있으면 409 `ROOM_QUESTION_ALREADY_EXISTS`(data에 그 질문) |
| 지금 질문 | GET | `/api/diary-rooms/{roomId}/questions/current` | 가장 최근 날짜 질문, 없으면 `data: null` |
| 답하기 | POST | `/api/diary-rooms/{roomId}/questions/{questionId}/answers` | `{content}` 1~200자(400 `ROOM_ANSWER_INVALID`), ready가 아니면 409 `ROOM_QUESTION_NOT_READY`, 학습 신호 기록 |
| 내 답 삭제 | DELETE | `/api/diary-rooms/{roomId}/questions/{questionId}/answers/me` | 답이 없어도 200, 학습 신호도 삭제 |
| 지난 질문 목록 | GET | `/api/diary-rooms/{roomId}/questions?page=&size=` | 최신순, size 최대 50, `answerCount` 포함 |

## 테스트

- `src/test/java/com/luvin/diary/domain`: `EmojiTest`(이모지 검사), `DiaryTest`(보기 권한) — DB 없이 실행
- `src/test/java/com/luvin/diary/api/DiaryApiIntegrationTest`: 실제 서버 + DB로 전체 API 시나리오
  (권한 403/404/400, 이모지 토글, 동시 반응 20번 → 1개, 인원 제한·동시 참여, 방 닉네임 중복 409·DB UNIQUE,
  방 삭제 연쇄 삭제(그룹 질문·학습 신호 포함), 일기·댓글 학습 신호, 피드 작성자 정보 등)
- CI(`./gradlew test`)가 PR마다 임시 Postgres로 자동 실행한다. 로컬은 Postgres를 띄우고 `DB_URL`, `JWT_SECRET` 등을 지정해서 실행.

## 남은 것

- 쿼리 파라미터 형식 오류(예: `kick?userId=abc`, `MethodArgumentTypeMismatchException`)는 아직 500으로 나간다.
  (없는 경로 404, 파라미터 누락 400은 `GlobalExceptionHandler`가 Spring의 `ErrorResponse` 상태를 그대로 써서 해결됨)
- 정렬(`?sort=`) 파라미터는 아직 없다(전부 최신순 고정)
- 멤버 목록 응답에 이메일이 나간다 → 개인정보라 빼는 것 검토
- 댓글 `content`에 길이 제한이 없다. 일기를 못 찾을 때 댓글 API는 `ErrorCode.NOT_FOUND`, 일기 API는 `DiaryNotFoundException`이라 메시지가 다르다
- 방 닉네임은 대소문자를 구분해서 비교한다(`Abc`와 `abc`는 다른 닉네임)

## 일정

### 김리원

| 날짜 | 개발 내용 |
|---|---|
| 9/18(금) | 일기 작성/목록조회 API |
| 9/19(토) | 일기 상세조회 + 공유방 생성/목록조회 API |
| 9/21(월) | 공유방 수정/삭제 API |
| 9/22(화) | 댓글 작성/조회 API |
| 9/23(수) | 댓글 수정/삭제 API |
| 9/24(목) | 공유방 멤버 목록조회 API |
| 9/25(금) | 정렬(`?sort=`) 파라미터 전체 적용 |
| 9/26(토) | 코드리뷰, 진행상황 크로스체크, 밀린 작업 처리 |
| 9/28(월) | 남은 버그/미완성 API 마무리 |
| 9/29(화) | Swagger 문서 최종 정리 |
| 9/30(수) | 발견된 버그 즉시 수정 |

### 임제민

| 날짜 | 개발 내용 |
|---|---|
| 9/18(금) | 일기 공개범위 설정(visibility) — Entity/Repository/Service/Controller |
| 9/19(토) | 일기 수정/삭제 API (Spring Security로 작성자 본인 체크) |
| 9/21(월) | 공감(POST)/공감취소(DELETE) — `@Transactional` + 동시성 처리(비관적 락 or 원자적 UPDATE 쿼리 고려) |
| 9/22(화) | 공유방 참여/나가기 API |
| 9/23(수) | 공유방 멤버 추가/강퇴(kick) — 방장 권한 체크(`@PreAuthorize` 등) |
| 9/24(목) | 공유방 일기 조회 API (QueryDSL/JPQL 조인, likeCount/commentCount 서브쿼리 또는 DTO 프로젝션) |
| 9/25(금) | 공유 일기 조회(커뮤니티) API — 조인/DTO 프로젝션 |
| 9/26(토) | 코드리뷰, 진행상황 크로스체크, 밀린 작업 처리 |
| 9/28(월) | 권한 검증 전체 점검, 응답 DTO 필드 일관성 점검(isMine, likeCount 등) |
| 9/29(화) | 마지막 통합 정리, 미완성분 최종 처리, Swagger 문서 최종 정리 |
| 9/30(수) | 단위테스트 + 통합테스트 전체 API 시나리오 테스트 / 발견된 버그 즉시 수정 |

## 구현 시 참고 (현재 프로젝트 기준)

- 현재 사용자 ID: `SecurityUtils.getCurrentUserId()`. 작성자 본인 체크와 방장 체크는 이 값으로 한다.
- `@PreAuthorize`: `SecurityConfig`에 `@EnableMethodSecurity`가 이미 켜져 있어서 바로 쓸 수 있다.
- QueryDSL은 `build.gradle`에 없다. 추가하려면 팀 확인이 먼저 필요하다(AGENT.md §3). 그 전에는 JPQL + DTO 프로젝션으로 한다.
- 예외: 다른 모듈처럼 `common/exception/`에 `DiaryNotFoundException` 등을 두고 `GlobalExceptionHandler`에 등록한다.
- 스키마: `ddl-auto: update`라서 엔티티를 바꾸면 테이블도 바로 바뀐다. 컬럼 삭제나 이름 변경은 `docs/DEPLOYMENT.md`의 하위 호환 원칙을 따른다.
- 정렬(`?sort=`)은 일기·공유방·댓글 목록에 같은 형식으로 적용한다(미구현).
- `@Modifying(clearAutomatically = true)`는 이미 불러온 엔티티를 떼어낸다. 그 뒤에 LAZY 연관관계(예: `diary.getRoom()`)를
  읽으면 `LazyInitializationException`이 난다. 같은 트랜잭션에서 엔티티를 계속 쓸 거면 붙이지 않는다.
- native `insert … on conflict`는 충돌 대상을 꼭 적는다(`on conflict (room_id, user_id)`). 대상 없이 `do nothing`이면
  닉네임 UNIQUE 같은 다른 제약 위반까지 조용히 무시되어 "추가된 줄 알았는데 안 들어간" 상태가 된다.

# diary — 감정일기 (인턴 ver)

일기 · 공유방 · 댓글 · 공감 기능 모듈. 기간: 9/18(금) ~ 9/30(수)

## 패키지 구조

```
diary/
├── controller/  DiaryController, DiaryRoomController, DiaryCommentController
├── service/     DiaryService, DiaryRoomService, DiaryCommentService
├── repository/  DiaryRepository, DiaryRoomRepository, DiaryCommentRepository
├── domain/      Diary, DiaryReaction, Emoji(반응 이모지 검사),
│                DiaryRoom, DiaryRoomMember, DiaryRoomMemberRole(enum), DiaryComment
└── dto/         DiaryDto, DiaryRoomDto, DiaryCommentDto
```

- controller / service / repository / dto는 일기 · 공유방 · 댓글 3개 단위로 맞춘다.
- 서비스는 인터페이스+Impl로 나누지 않고 `@Service` 클래스 하나로 둔다.
- 도메인(엔티티)은 테이블 단위라서 3개로 합칠 수 없다(아래 ERD의 테이블 5개).
  - `DiaryReaction`, `DiaryRoomMember`는 ERD 규칙대로 id 없이 복합 PK를 쓴다
    (엔티티 안의 `@EmbeddedId Pk` 클래스).
  - 이 둘은 레포지토리를 따로 만들지 않는다. `DiaryReaction`은 `DiaryRepository`에서, `DiaryRoomMember`는
    `DiaryRoomRepository`에서 연관관계(cascade)나 `@Query`로 같이 다룬다.
- DTO는 도메인당 파일 하나다. 요청/응답은 그 안의 중첩 클래스로 둔다
  (`DiaryDto.CreateRequest`, `DiaryDto.FeedItem`, `DiaryRoomDto.MemberResponse` 등).

## 확정된 설계 (구현 중 변경된 것)

- **공개범위 없음. 일기는 항상 공유방 하나 안에서 쓴다.** 볼 수 있는 사람은 작성자 + 그 방의 방장·멤버(`Diary.canBeViewedBy`).
  작성 시 `roomId` 필수, 수정은 제목·내용만(방 이동 불가).
- **공유방은 초대 방식.** 스스로 참여하는 API는 없고 방장이 멤버를 추가한다. 방장은 나갈 수 없다(400).
  방장 확인은 `diary_room.owner_id` 하나로만 한다. 방을 만들면 방장이 `OWNER`로 멤버 테이블에도 들어간다.
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
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
}

table diary_room_member [note: '공유방 멤버'] {
  room_id bigint [not null]
  user_id bigint [not null]
  role room_member_role [not null, default: 'member']
  joined_at timestamptz [not null]
  indexes { (room_id, user_id) [pk] }
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
```

> - enum은 기존 모듈처럼 `@Enumerated(EnumType.STRING)`으로 저장한다. DB에는 PG enum 타입이 아니라 문자열
>   컬럼으로 들어가고, 값은 Java enum 이름(`OWNER`, `MEMBER`)이다.
> - 나중에 팀 전체가 ERD(`uuid`)로 옮기면 diary의 사용자 컬럼도 그때 같이 `uuid`로 바꾼다.

### 엔티티 매핑 상태 (DB 연결까지 완료)

- 위 ERD의 컬럼은 엔티티에 전부 매핑되어 있다. `ddl-auto: update`로 부팅하면 테이블이 생성된다(임시 Postgres로 확인).
- 연관관계: `Diary.room`, `DiaryComment.diary`는 `@ManyToOne`이다. `DiaryReaction.diary`, `DiaryRoomMember.room`은
  `@MapsId`로 복합 PK의 일부다.
- 사용자(`user_id`, `owner_id`)는 기존 모듈처럼 `Long` 컬럼만 둔다. 예외: `DiaryRoomMember.member`는 `User`와
  연관관계라 `diary_room_member.user_id`에는 `users`로 가는 FK가 생긴다.
- ERD의 `delete: cascade / set null`은 DB FK에 반영되지 않는다(Hibernate 기본값은 NO ACTION).
  그래서 서비스에서 순서대로 먼저 지운다.
  - 일기 삭제: 반응 → 댓글 → 일기
  - 공유방 삭제: 방 일기들의 반응 → 댓글 → 일기 → 멤버 → 방
- 로컬 DB에 예전 구조(`visibility` 컬럼 등)가 남아 있으면 `ddl-auto: update`가 컬럼을 지우지 않아 저장이 실패한다.
  구조가 바뀐 뒤에는 diary 테이블 5개를 지우고 다시 띄운다.

## API 목록 (구현 완료 기준)

Swagger(`/swagger-ui`)의 "감정일기", "감정일기 공유방", "감정일기 댓글" 태그에서도 볼 수 있다.

### 일기 — `DiaryController` (`/api/diaries`)

| 기능 | Method | Path | 비고 |
|---|---|---|---|
| 일기 작성 | POST | `/api/diaries` | `{roomId, title, content}`, 방장·멤버만 |
| 내 일기 목록 | GET | `/api/diaries?page=&size=` | 내가 쓴 것만, size 최대 50 |
| 일기 상세 | GET | `/api/diaries/{diaryId}` | 작성자·방장·멤버만 |
| 일기 수정 | PUT | `/api/diaries/{diaryId}` | `{title, content}`, 작성자만 |
| 일기 삭제 | DELETE | `/api/diaries/{diaryId}` | 작성자만, 반응·댓글도 삭제 |
| 이모지 반응 토글 | POST | `/api/diaries/{diaryId}/like` | `{emoji}` → `{diaryId, emoji, liked, likeCount}` |
| 내 방들 피드 | GET | `/api/diaries/community` | 내가 속한 방들의 일기 최신 50개 |

### 공유방 — `DiaryRoomController` (`/api/diary-rooms`)

| 기능 | Method | Path | 비고 |
|---|---|---|---|
| 공유방 생성 | POST | `/api/diary-rooms` | `{name, description}`, 만든 사람이 OWNER |
| 내 공유방 목록 | GET | `/api/diary-rooms` | 최근 참여한 방이 먼저 |
| 공유방 수정 | PUT | `/api/diary-rooms/{roomId}` | 방장만 |
| 공유방 삭제 | DELETE | `/api/diary-rooms/{roomId}` | 방장만, 방의 일기·반응·댓글·멤버도 삭제 |
| 멤버 목록 | GET | `/api/diary-rooms/{roomId}/members` | 방장·멤버만 |
| 멤버 추가(초대) | POST | `/api/diary-rooms/{roomId}/members` | `{userId}`, 방장만 |
| 나가기 | DELETE | `/api/diary-rooms/{roomId}/members/me` | 방장은 400 |
| 강퇴 | DELETE | `/api/diary-rooms/{roomId}/members/kick?userId=` | 방장만, 자기 자신은 400 |
| 공유방 일기 조회 | GET | `/api/diary-rooms/{roomId}/diaries` | 방장·멤버만 |

### 댓글 — `DiaryCommentController` (`/api/diaries/{diaryId}/comments`)

| 기능 | Method | Path | 비고 |
|---|---|---|---|
| 댓글 작성 | POST | `/api/diaries/{diaryId}/comments` | 일기를 볼 수 있는 사람만 |
| 댓글 목록 | GET | `/api/diaries/{diaryId}/comments` | 작성순 |
| 댓글 수정 | PUT | `/api/diaries/{diaryId}/comments/{commentId}` | 작성자만 |
| 댓글 삭제 | DELETE | `/api/diaries/{diaryId}/comments/{commentId}` | 작성자만 |

## 테스트

- `src/test/java/com/luvin/diary/domain`: `EmojiTest`(이모지 검사), `DiaryTest`(보기 권한) — DB 없이 실행
- `src/test/java/com/luvin/diary/api/DiaryApiIntegrationTest`: 실제 서버 + DB로 전체 API 시나리오
  (권한 403/404/400, 이모지 토글, 동시 반응 20번 → 1개, 방 삭제 연쇄 삭제, 피드 작성자 정보 등)
- CI(`./gradlew test`)가 PR마다 임시 Postgres로 자동 실행한다. 로컬은 Postgres를 띄우고 `DB_URL`, `JWT_SECRET` 등을 지정해서 실행.

## 남은 것

- 없는 경로 요청, 쿼리 파라미터 누락/형식 오류가 500으로 나간다 → `GlobalExceptionHandler`(공통)에
  `NoResourceFoundException`(404), `MissingServletRequestParameterException`·`MethodArgumentTypeMismatchException`(400) 추가 필요
- 정렬(`?sort=`) 파라미터는 아직 없다(전부 최신순 고정)
- 멤버 목록 응답에 이메일이 나간다 → 개인정보라 빼는 것 검토
- 빵 타입 계산(설문 채점)은 master에만 있다. 머지 전까지 이 브랜치에서는 `authorBreadType`이 null일 수 있다

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

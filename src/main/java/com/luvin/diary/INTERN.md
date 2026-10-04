# diary — 감정일기 (인턴 ver)

일기 · 공유방 · 댓글 · 공감 기능 모듈. 기간: 9/18(금) ~ 9/30(수)

## 패키지 구조

```
diary/
├── controller/  DiaryController, DiaryRoomController, DiaryCommentController
├── service/     DiaryService, DiaryRoomService, DiaryCommentService
├── repository/  DiaryRepository, DiaryRoomRepository, DiaryCommentRepository
├── domain/      Diary, DiaryReaction, DiaryVisibility(enum),
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
  (`DiaryDto.Request`, `DiaryDto.Response`, `DiaryRoomDto.MemberResponse` 등). 작성/수정, 목록/상세는 같은 클래스를 같이 쓴다.

## ERD (팀 ERD 규칙 + 기존 DB 기준)

팀 ERD 규칙: PostgreSQL 18, 단수형 테이블 이름, `id bigint GENERATED ALWAYS AS IDENTITY`,
`timestamptz`, 연결 테이블은 복합 PK, 문자열 길이는 백엔드에서 관리.

**기존 DB에서 돌린다.** 팀 ERD는 `user.id uuid`지만 지금 DB는 `users.user_id bigint`라서, diary의 사용자 컬럼
(`user_id`, `owner_id`)은 `bigint`로 두고 `users.user_id`를 가리킨다. 값은 `SecurityUtils.getCurrentUserId()`(Long) 그대로 쓴다.

일기와 공유방은 **일기 1개 → 공유방 최대 1개**다(`diary.room_id`, nullable). 방 하나에는 일기가 여러 개 있을 수 있다.

```
enum diary_visibility {
  private   // 나만 보기
  room      // 공유방 멤버만
  public    // 커뮤니티 공개
}

table diary [note: '감정일기'] {
  id bigint [pk, increment]
  user_id bigint [not null]
  room_id bigint            // 공유방에 올린 일기만 값이 있음
  title text [not null]
  content text [not null]
  visibility diary_visibility [not null]
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
}

table diary_reaction [note: '일기 공감'] {
  diary_id bigint [not null]
  user_id bigint [not null]
  created_at timestamptz [not null]
  indexes { (diary_id, user_id) [pk] }   // 중복 공감을 DB에서 막음
}

enum room_member_role {
  owner
  member
}

table diary_room [note: '공유방'] {
  id bigint [pk, increment]
  owner_id bigint [not null]
  name text [not null]
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
Ref: diary.room_id > diary_room.id [delete: set null]
Ref: diary_reaction.diary_id > diary.id [delete: cascade]
Ref: diary_reaction.user_id > users.user_id [delete: restrict]
Ref: diary_room.owner_id > users.user_id [delete: restrict]
Ref: diary_room_member.room_id > diary_room.id [delete: cascade]
Ref: diary_room_member.user_id > users.user_id [delete: restrict]
Ref: diary_comment.diary_id > diary.id [delete: cascade]
Ref: diary_comment.user_id > users.user_id [delete: restrict]
```

> - enum은 기존 모듈처럼 `@Enumerated(EnumType.STRING)`으로 저장한다. DB에는 PG enum 타입이 아니라 문자열
>   컬럼으로 들어가고, 값은 Java enum 이름(`PRIVATE`, `OWNER` 등)이다.
> - 나중에 팀 전체가 ERD(`uuid`)로 옮기면 diary의 사용자 컬럼도 그때 같이 `uuid`로 바꾼다.

### 엔티티 매핑 상태 (DB 연결까지 완료)

- 위 ERD의 컬럼은 엔티티에 전부 매핑되어 있다. `ddl-auto: update`로 부팅하면 테이블이 생성된다(임시 Postgres로 확인).
- 연관관계: `Diary.room`, `DiaryComment.diary`는 `@ManyToOne`이다. `DiaryReaction.diary`, `DiaryRoomMember.room`은
  `@MapsId`로 복합 PK의 일부다.
- 사용자(`user_id`, `owner_id`)는 기존 모듈처럼 `Long` 컬럼만 둔다. `users`로 가는 FK는 DB에 생기지 않는다.
- ERD의 `delete: cascade / set null`은 DB FK에 반영되지 않는다(Hibernate 기본값은 NO ACTION).
  일기나 방을 지울 때 딸린 공감, 댓글, 멤버는 서비스에서 먼저 지우거나 연관관계 cascade로 처리한다.

## API 목록 (초안 — 경로는 구현하면서 확정)

### 일기 — `DiaryController` (`/api/diaries`)

| 기능 | Method | Path | 담당 |
|---|---|---|---|
| 일기 작성 | POST | `/api/diaries` | 김리원 |
| 일기 목록 조회 | GET | `/api/diaries?sort=` | 김리원 |
| 일기 상세 조회 | GET | `/api/diaries/{diaryId}` | 김리원 |
| 일기 수정 | PUT | `/api/diaries/{diaryId}` | 임제민 |
| 일기 삭제 | DELETE | `/api/diaries/{diaryId}` | 임제민 |
| 공개범위 설정 | (작성/수정 요청의 `visibility`) | | 임제민 |
| 공감 | POST | `/api/diaries/{diaryId}/reactions` | 임제민 |
| 공감 취소 | DELETE | `/api/diaries/{diaryId}/reactions` | 임제민 |
| 공유 일기 조회(커뮤니티) | GET | `/api/diaries/community?sort=` | 임제민 |

### 공유방 — `DiaryRoomController` (`/api/diary-rooms`)

| 기능 | Method | Path | 담당 |
|---|---|---|---|
| 공유방 생성 | POST | `/api/diary-rooms` | 김리원 |
| 공유방 목록 조회 | GET | `/api/diary-rooms?sort=` | 김리원 |
| 공유방 수정 | PUT | `/api/diary-rooms/{roomId}` | 김리원 |
| 공유방 삭제 | DELETE | `/api/diary-rooms/{roomId}` | 김리원 |
| 공유방 멤버 목록 조회 | GET | `/api/diary-rooms/{roomId}/members` | 김리원 |
| 공유방 참여 | POST | `/api/diary-rooms/{roomId}/join` | 임제민 |
| 공유방 나가기 | DELETE | `/api/diary-rooms/{roomId}/members/me` | 임제민 |
| 멤버 추가 (방장) | POST | `/api/diary-rooms/{roomId}/members` | 임제민 |
| 멤버 강퇴 (방장) | DELETE | `/api/diary-rooms/{roomId}/members/{memberId}` | 임제민 |
| 공유방 일기 조회 | GET | `/api/diary-rooms/{roomId}/diaries?sort=` | 임제민 |

### 댓글 — `DiaryCommentController` (`/api/diaries/{diaryId}/comments`)

| 기능 | Method | Path | 담당 |
|---|---|---|---|
| 댓글 작성 | POST | `/api/diaries/{diaryId}/comments` | 김리원 |
| 댓글 조회 | GET | `/api/diaries/{diaryId}/comments?sort=` | 김리원 |
| 댓글 수정 | PUT | `/api/diaries/{diaryId}/comments/{commentId}` | 김리원 |
| 댓글 삭제 | DELETE | `/api/diaries/{diaryId}/comments/{commentId}` | 김리원 |

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
- 정렬(`?sort=`)은 일기·공유방·댓글 목록에 같은 형식으로 적용한다(9/25).

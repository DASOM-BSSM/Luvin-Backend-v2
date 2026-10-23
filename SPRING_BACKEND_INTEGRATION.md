# Spring Backend 연동 구현 가이드

> Spring 담당자 인계와 구현 수락 기준은 `SPRING_BACKEND_REQUIREMENTS.md`의
> 「Spring Backend 연동 요구사항 명세서」를 우선합니다. 이 문서는 endpoint별 연동 설명을 보충하는
> 참고 자료입니다.

이 문서는 Spring 메인 백엔드가 Luvin AI 서비스와 연동할 때 구현해야 하는 항목을
`openapi.json`과 현재 배포된 MVP 동작을 기준으로 정리합니다. React Native 앱은 AI 서비스를
직접 호출하지 않습니다. 회원·설문 점수·사용자 선택·미니게임 결과의 권위는 Spring에 있으며,
AI 서비스는 대화 생성과 생성 상태만 담당합니다.

## 1. 계약과 환경 설정

- `openapi.json`을 Spring DTO/client 생성의 기준으로 사용합니다.
- base URL을 코드에 고정하지 않고 `AI_SERVICE_BASE_URL` 환경변수로 관리합니다. ECS staging에서는
  Service Connect client인 Spring task에 `http://luvin-ai:8000`을 설정합니다.
- Spring ECS 서비스는 `luvin-staging.internal` Service Connect namespace에 참여하고 Terraform 출력
  `service_connect_client_security_group_id`의 security group을 연결해야 합니다. client-only Spring
  서비스의 Service Connect 설정에는 endpoint를 정의하는 `service` block이 필요하지 않습니다.
- 공인 task IP는 개발 PC의 임시 테스트에만 사용합니다. Fargate task 교체 시 바뀌므로 Spring 설정에
  저장하지 않습니다. Service Connect 연결은 현재 HTTP이며 service JWT와 TLS는 production 전 별도로
  완료해야 합니다.
- 모든 요청에 `X-Owner-Subject`를 전달합니다. 값은 Spring이 인증한 사용자를 나타내는 불투명하고
  안정적인 식별자여야 하며 이메일, 전화번호 같은 개인정보를 넣지 않습니다.
- 모든 mutation 요청에는 재시도 동안 동일하게 유지되는 `Idempotency-Key` UUID를 전달합니다.
- JSON 필드명은 `snake_case`, 식별자는 UUID, 시간은 UTC를 사용합니다.

현재 `X-Owner-Subject`는 임시 신뢰 헤더일 뿐 service JWT 검증이 구현된 상태가 아닙니다. 모바일
앱에서 이 헤더를 받아 그대로 전달하거나 AI endpoint를 앱에 공개하면 안 됩니다.

## 2. Spring에 필요한 구성요소

### AI 서비스 client

다음 기능을 가진 내부 HTTP client를 구성합니다.

- base URL, 연결 timeout, 응답 timeout 설정
- `X-Owner-Subject`와 `Idempotency-Key` 주입
- OpenAPI DTO 직렬화·역직렬화
- HTTP 상태와 AI 오류를 Spring 도메인 오류로 변환
- 요청 ID, season ID, job ID를 포함한 구조화 로그
- API key나 전체 사용자 성향 payload를 로그에 남기지 않는 redaction

에피소드 생성은 비동기 job이므로 HTTP 요청 thread에서 완료될 때까지 기다리지 않습니다. 생성
POST는 `202 Accepted`와 `status_url`을 받은 뒤 종료하고, 별도 scheduler 또는 영속 작업으로 job을
polling합니다. polling은 1초부터 시작해 최대 5초까지 backoff하고 전체 제한 시간을 둡니다.

### 영속 데이터

Spring DB에는 최소한 다음 데이터를 저장합니다.

| 데이터 | 용도 |
|---|---|
| Spring 사용자 ID ↔ AI `season_id` | owner 범위와 시즌 복구 |
| 최신 `revision` | mutation 낙관적 동시성 |
| episode별 `job_id`, status | polling 재개와 중복 요청 방지 |
| episode별 `version_id` | 페이지 조회 중 버전 고정 |
| 마지막 조회 `sequence` | 앱 표시 진행도 |
| 1화 사용자 선택 | 2화 생성 조건 |
| 3화 미니게임 결과와 이벤트 ID | 4화 생성 조건 및 중복 방지 |

AI 내부 관계 점수, 기분 수치, prompt, state snapshot을 Spring이나 앱의 사용자 응답 모델로 복제하지
않습니다. 실제 사용자의 설문 점수와 미니게임 판정은 계속 Spring이 소유합니다.

## 3. 시즌 생성

`POST /v1/seasons`

필수 header:

```text
X-Owner-Subject: <Spring의 불투명 사용자 식별자>
Idempotency-Key: <요청마다 생성하고 재시도 시 재사용하는 UUID>
```

Spring이 계산한 13개 성향 점수를 `representative.traits`에 전달합니다. 모든 값은 숫자
`1..100`이고 boolean, 문자열, NaN, Infinity는 허용되지 않습니다.

```text
affection_expression
relationship_anxiety
relationship_avoidance
emotional_attunement
relationship_initiative
practical_priority
reassurance_need
jealousy_reactivity
relationship_energy_dependence
emotional_suppression
conflict_confrontation
relationship_pace
interest_expression_frequency
```

MVP에서 지원하는 버전은 `pool.v1`, `topics.v1`뿐입니다. 생략하면 이 기본값이 적용됩니다.
응답의 `characters`를 저장하거나 캐시하고, `role=representative`인 캐릭터 ID를 사용자 대변 AI로
사용합니다. 메시지의 `speaker_id`가 이 ID와 같으면 사용자 대변 AI 발화입니다.

## 4. 에피소드 생성과 조회

### 생성 요청

`POST /v1/seasons/{season_id}/episodes/{number}/generations`

요청 직전에 `GET /v1/seasons/{season_id}`로 최신 `revision`을 읽고 `expected_revision`에 넣습니다.
오래된 revision이면 `409`가 반환되므로 최신 시즌을 다시 조회한 뒤 사용자 동작의 유효성을 재판단합니다.

### job polling

`GET /v1/jobs/{job_id}`

| 상태 | Spring 처리 |
|---|---|
| `queued`, `running`, `retry_wait` | backoff 후 재조회 |
| `succeeded` | `result_version_id` 저장 후 에피소드 조회 |
| `failed` | `error_code` 기록, 앱에 재시도 가능한 실패로 변환 |

네트워크 timeout은 job 실패를 의미하지 않습니다. 동일 generation POST를 즉시 새 key로 다시 보내지
말고 기존 `job_id`를 우선 조회합니다.

### job 진행 이벤트(SSE) 중계

AI `GET /v1/jobs/{job_id}/events`를 Spring이 구독해 앱에 `GET /api/ai/jobs/{jobId}/events`로 그대로 넘깁니다
(`AiJobEventStreamService`). polling은 그대로 두고, SSE는 진행 상황을 빨리 보여주는 보조 수단입니다.

- `jobId`는 generation/쪽지 요청 응답의 `jobId`이고, 내 job이 아니면 404(JSON)입니다. 같은 회차를 다시
  요청해 `jobId`가 바뀌면 이전 job은 더 이상 구독할 수 없습니다.
- 이벤트 이름·`id`·`data`(snake_case)는 AI가 보낸 그대로입니다. `delta.text`는 검증 전 JSON 조각이라 segment별로
  모았다가 `segment_end`에서 처리하고 `segment_error`면 버립니다. `completed`/`error`가 오면 Spring이 연결을 닫습니다.
- `completed`/`error`는 Spring이 그 job 상태를 먼저 DB에 반영한 뒤(`AiJobPollingScheduler.pollJobNow`) 앱에 넘깁니다.
  그래서 앱이 `completed`를 받자마자 조회 API를 부르면 결과가 나옵니다. 이 반영이 실패해도(로그만 남김) `completed`는
  그대로 넘기므로, 드물게 조회가 409(아직 완료되지 않음)면 잠시 후 다시 조회합니다 — 다음 polling 차례에 반영됩니다.
  `segment_end` 시점에는 아직 저장 전이라 조회 API에 결과가 없습니다.
- 재연결할 때는 마지막으로 받은 `id`를 `Last-Event-ID` 헤더(또는 `after_event_id` query)로 보내면 AI에 그대로 전달됩니다.
- AI 쪽 구독이 실패하거나 `completed`/`error` 없이 끊기면 `stream_error` 이벤트(`{"error_code": ...}`)를 보내고 닫습니다.
  - `INVALID_LAST_EVENT_ID`: AI가 보낸 id가 잘못됐다고 거절(400). 같은 id로 다시 붙지 말고 id 없이 붙거나 polling으로 돌아갑니다.
  - `JOB_NOT_FOUND`: AI에 job이 없음(404). polling으로 상태를 확인합니다.
  - `AI_UNAVAILABLE`, `STREAM_INTERRUPTED`, `INTERNAL_ERROR`: 마지막으로 받은 id로 다시 붙거나 polling으로 돌아갑니다.
- AI는 이벤트가 없을 때 15초마다 `: keep-alive` 주석을 보내고(Spring은 무시), 재연결 시 `Last-Event-ID`보다 큰 id부터 다시 보냅니다.
  job 상태로 보충 생성한 종료 이벤트에는 id가 없을 수 있지만, 종료 이벤트라 재연결에는 영향이 없습니다.
- 15초마다 `: keep-alive` 주석을 보내고, 10분이 지나면 닫습니다. 서버 한 대당 동시 스트림은 200개까지이고, 넘으면 503입니다
  (`app.ai.stream.*`로 조정).
- 앱이 연결을 끊어도 AI job은 취소하지 않고 구독만 닫습니다.
- 운영 경로는 ALB → Spring 컨테이너뿐이라(nginx·응답 압축 없음) SSE가 버퍼링되지 않습니다. ALB idle timeout(60초)보다
  keep-alive(15초)가 짧아 이벤트가 없어도 끊기지 않습니다. 앞에 CDN이나 nginx를 두게 되면 버퍼링 설정을 다시 확인합니다.

#### 재현 절차 (`TOKEN`, `BASE`는 환경에 맞게)

```bash
# 1. generation 성공: 응답 data.jobId로 바로 구독 → segment_* / delta … completed 후 닫힘
curl -s -X POST "$BASE/api/ai/seasons/episodes/1/generations" -H "Authorization: Bearer $TOKEN"
curl -N "$BASE/api/ai/jobs/$JOB_ID/events" -H "Authorization: Bearer $TOKEN" -H "Accept: text/event-stream"
curl -s "$BASE/api/ai/seasons/episodes/1?season_id=$SEASON_ID" -H "Authorization: Bearer $TOKEN"   # completed 직후 200

# 2. 끝난 job 재구독: 1이 끝난 뒤 같은 jobId로 다시 붙으면 이벤트를 처음부터 다시 받고 completed로 닫힘
# 3. 재연결: 받은 id 중 하나를 Last-Event-ID로 보내면 그 다음 id부터 받음
curl -N "$BASE/api/ai/jobs/$JOB_ID/events" -H "Authorization: Bearer $TOKEN" -H "Last-Event-ID: $ID"
# 4. stream_error: 잘못된 id → {"error_code":"INVALID_LAST_EVENT_ID"} 후 닫힘
curl -N "$BASE/api/ai/jobs/$JOB_ID/events" -H "Authorization: Bearer $TOKEN" -H "Last-Event-ID: not-a-cursor"
# 5. HTTP 오류: 토큰 없이 → 401, 다른 계정 토큰 → 404
```

job 자체를 실패시키는 방법(`error`, `segment_error`)은 앱에서 만들 수 없습니다. 실제 AI로 재현하려면 AI 쪽 장애 주입이
필요하고, Spring 중계 동작은 `AiJobEventStreamIntegrationTest`(가짜 AI 서버)로 검증합니다.

### 메시지 페이지 조회

`GET /v1/seasons/{season_id}/episodes/{number}`

첫 페이지에서 반환된 `version_id`를 저장하고 이후 페이지에는 같은 `version_id`를 전달합니다.
`after_sequence`는 마지막으로 받은 메시지의 `sequence`이며 exclusive cursor입니다. `has_more=false`가
될 때까지 조회합니다. AI 쪽에서 활성 버전이 바뀌어 `VERSION_SUPERSEDED`가 반환되면 기존 페이지를
섞지 말고 새 version의 첫 페이지부터 다시 읽습니다.

1·3화 응답의 `topic`은 서버가 시즌 seed로 선택한 불변 snapshot입니다.

```json
{
  "id": "weekend_recharge",
  "title": "쉬는 날에 가장 편하게 재충전하는 방법",
  "category": "daily_life",
  "config_version": "topics.v1"
}
```

2·4·5화에서는 `topic=null`입니다. Spring은 메시지 내용에서 주제를 다시 추론하지 않습니다.

## 5. 에피소드 진행 순서

1. 시즌 생성 후 에피소드 1 generation을 요청합니다.
2. 에피소드 1 완료 후 사용자가 고른 candidate로 1화 selection을 저장합니다.
3. 최신 revision으로 에피소드 2 generation을 요청합니다.
4. 에피소드 3 generation을 요청하고 완료된 대화를 표시합니다.
5. Spring이 미니게임을 실행·판정한 뒤 3화 selection을 저장합니다.
6. 최신 revision으로 에피소드 4 generation을 요청합니다.
7. 에피소드 5 generation을 요청합니다.
8. 완료 후 `GET /v1/seasons/{season_id}/report`로 최종 리포트를 조회합니다.

앱 화면 상태만으로 진행 가능 여부를 결정하지 말고 최신 시즌과 API 결과를 기준으로 처리합니다.

## 6. 선택과 미니게임

### 에피소드 1 선택

`POST /v1/seasons/{season_id}/episodes/1/selection`

- `source=user`
- `partner_id`: 응답의 candidate ID 중 하나
- `source_event_id`: Spring이 생성한 불변 사용자 선택 이벤트 UUID
- `expected_revision`: 최신 시즌 revision

### 에피소드 3 미니게임 선택

`POST /v1/seasons/{season_id}/episodes/3/selection`

- `source=minigame`
- `source_event_id`: Spring 미니게임 결과 이벤트의 불변 UUID
- 성공: `game_result=success`, 검증된 `partner_id` 전달
- 실패: `game_result=failure`, `partner_id`를 전달하지 않음

미니게임 성공 여부와 성공 시 대상 판정은 Spring 책임입니다. LLM이나 AI 서비스에 미니게임 판정을
위임하지 않습니다.

## 7. reroll (제거됨)

다시 굽기(reroll)는 제거했습니다. Spring에는 reroll API·polling이 없고, `ai_reroll_requests` 테이블은
V9 마이그레이션으로 사용 중단만 표시했습니다(데이터는 남아 있음).

## 8. 오류 처리

Spring client는 최소한 다음 HTTP 상태를 구분합니다.

| HTTP | 처리 |
|---|---|
| 401 | 내부 인증/header 구성 오류; 사용자 재로그인으로 자동 치환하지 않음 |
| 404 | owner 불일치 또는 리소스 없음; 다른 사용자의 존재를 노출하지 않음 |
| 409 | revision, 진행 순서, selection 또는 version 충돌 |
| 422 | DTO·범위·페이지 파라미터 오류; 재시도하지 않음 |
| 429 | backoff 후 제한적으로 재시도 |
| 5xx/timeout | 기존 job 존재 여부를 확인한 뒤 제한적으로 재시도 |

현재 일부 오류는 표준 `error` envelope가 아니라 FastAPI의 `detail` 형식으로 반환됩니다. Spring은
HTTP status를 우선 기준으로 처리하고 문자열/객체 `detail`을 모두 수용해야 합니다.

## 9. 현재 AI 서비스에서 추가 구현이 필요한 계약

다음 항목은 문서 목표에는 있으나 현재 MVP/OpenAPI에는 완성되어 있지 않습니다. Spring 연동을
운영으로 전환하기 전에 AI 서비스와 함께 확정해야 합니다.

- service JWT의 issuer, audience, JWKS, scope 검증
- Service Connect 내부 연결의 TLS와 Spring service JWT 인증
- season 생성 이외 mutation의 완전한 idempotency replay 저장
- 표준 오류 envelope와 `request_id`
- `Retry-After`와 job deadline/재시도 정책의 wire 반영
- 시즌 삭제 endpoint와 삭제 job
- worker lease 만료·재claim과 시도 ledger
- 실제 주제 유지, 페르소나 일관성, 지식 누출에 대한 자동 품질 gate

Spring은 위 미완성 기능이 이미 제공된다고 가정해 우회 구현하지 않습니다. 특히 앱 요청 header를
그대로 신뢰하거나 timeout 때마다 새로운 generation job을 만드는 방식은 금지합니다.

## 10. Spring 측 검증 체크리스트

- OpenAPI generated DTO가 checked-in `openapi.json`과 일치하는지 CI에서 검사
- 13개 성향의 누락, 범위, 숫자 타입 검증
- `X-Owner-Subject`가 사용자별로 안정적이고 개인정보가 아닌지 검증
- mutation 재시도 시 같은 idempotency key를 재사용하는지 검증
- job polling이 서버 재시작 후에도 복구되는지 통합 테스트
- revision 충돌 시 최신 시즌을 다시 읽는지 테스트
- speaker ID를 season characters와 매핑하는지 테스트
- pagination 중 version을 고정하는지 테스트
- 1·3화 topic 표시와 2·4화 null 처리를 테스트
- 미니게임 실패 시 partner ID를 보내지 않는지 테스트
- AI 내부 수치와 prompt가 앱 응답에 노출되지 않는지 contract test

## 11. 장면 블록 회차 (1~10화)

에피소드를 장면 블록 목록으로 재생하는 새 흐름입니다. 회차 구성과 수락·거절 판정은 Spring 코드 규칙
(`AiEpisodePlanner`, `AiChoiceJudge`, 수치는 `AiChoiceRules`)으로 정하고, 대사·관계 정리·쪽지·리포트만
FastAPI story endpoint(`AiStoryClient`)로 생성합니다. Spring은 Gemini를 직접 호출하지 않습니다.

### 앱 API

| 메서드 | 경로 | 내용 |
|---|---|---|
| PUT | `/api/users/me` | 기존 프로필 수정에 `age` 추가(1~120). 입력 전이면 `GET /api/users/me`의 `age`가 null |
| GET | `/api/ai/seasons/me` | 기존 응답에 `story`(setting, currentOrder, route, romancePartnerId, choices, breads[].statusText) 추가. 1화 요청 전이면 null |
| GET | `/api/ai/seasons/episodes/{order}/content` | 회차 블록. 처음 요청이면 생성을 시작하고 `status: generating` |
| POST | `/api/ai/seasons/episodes/{order}/choices` | `{ pointId, breadId \| null }` → choice_result 블록 + 시즌 상태 |
| POST | `/api/ai/seasons/episodes/{order}/complete` | 관계 기록 갱신 + 회차 쪽지. 10화면 최종 리포트 + 시즌 완료 |

- 블록 JSON 형식과 오류 code는 `openapi.json`(`AiEpisodeContentView`, `AiSceneBlockView`)을 따릅니다.
- 생성 중에는 jobId가 없어 job SSE를 쓰지 않습니다. `status`가 `ready`가 될 때까지 GET content를 1~3초 간격으로
  다시 부릅니다. `failed`면 다시 부를 때 생성을 다시 시작합니다.
- 진행 순서: 다음 회차는 이전 회차를 완료해야 생성되고(409 `PREVIOUS_EPISODE_NOT_COMPLETED`), 선택 회차(3·5·6·8화)는
  선택해야 완료됩니다(409 `CHOICE_REQUIRED`). 그래서 4화는 ①, 6화는 ②, 7화는 ③, 9화는 ④ 이후에만 생성됩니다.
- 나이가 없으면 1화 요청이 409 `AGE_REQUIRED`입니다. 배경은 1화를 처음 요청할 때 정해 시즌 동안 고정합니다
  (19세 이하 school, 20~23세 campus/cafe 랜덤, 24세 이상 office). 도중에 나이를 바꾸면 다음 시즌부터 반영됩니다.
- 쪽지(`GET /letters/unread`)와 최종 리포트(`GET /report`)는 경로·응답 형식이 그대로입니다. 회차마다 쪽지가 하나씩
  생기고 10화 쪽지가 시즌 종료 쪽지입니다. 시즌이 완료되면 기존처럼 `POST /next`로 다음 시즌을 시작합니다.
- 기존 5화 흐름 API(generations, 1화/3화 selection, 메시지 페이지 조회, 피드백, 좋아요)는 @Deprecated이고 동작은
  그대로입니다. 프론트 전환 후 별도 작업으로 지웁니다.

### FastAPI story endpoint (계약 합의 전)

`app.ai.story.client`가 `stub`(기본)이면 Spring 안의 stub이 정해진 문구로 응답하고, `http`면 아래 임시 경로로 보냅니다.
경로와 필드는 AI 문서(luvin_에피소드_재구성_AI_프롬프트.md)를 기준으로 AI 쪽과 합의해 확정합니다.
요청 DTO는 `com.luvin.ai.client.story` 패키지에 있습니다.

| 경로(임시) | 호출 시점 | 응답 |
|---|---|---|
| `POST /v1/story/stage-intro` | 1화 생성 전 | 출연진 배경별 역할·소개 |
| `POST /v1/story/episode` | 회차 생성 | intro + 블록 대사 |
| `POST /v1/story/interview` | 10화 생성 | 인터뷰 블록 대사 |
| `POST /v1/story/choice-result` | 선택 | 결과 장면(+ 거절이면 대체 장면) 대사 |
| `POST /v1/story/summarize` | 회차 완료 | 회차 요약, 빵별 관계 기록 갱신안, 호감 변화 제안(-5~+5) |
| `POST /v1/story/episode-note` | 회차 완료 | 기존 쪽지 형식(`LetterResponseDto`) |
| `POST /v1/story/season-report` | 10화 완료 | 기존 리포트 형식(`ReportResponseDto`) |

- 모든 호출에 `context`(setting, audience `minor|adult`, ageBand, 출연진·무대 소개, 관계 기록, 선택 기록과 사유 코드,
  내 빵 학습 데이터)를 함께 보냅니다.
- 응답은 Spring에서 다시 검증하고(`AiStoryResponseValidator`: 요청한 블록 id·참여 빵 일치, 빈 대사·길이 초과 금지),
  실패하면 1회 재시도, 그래도 실패하면 저장하지 않고 오류를 돌려줍니다(502 `STORY_GENERATION_FAILED`).
- 생성 호출 timeout은 `app.ai.story.read-timeout`(기본 60초)입니다.

### 개발 전용

`app.dev-tools.enabled=true`인 환경에서만 `POST /api/dev/ai/seasons/current/story/reset`이 있습니다(운영 기본값 false).
현재 시즌의 선택·관계 기록·회차·회차 쪽지·리포트를 지우고 1화 전으로 되돌립니다.

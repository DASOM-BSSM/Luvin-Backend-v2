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
| reroll 요청 key와 선택 상대 | 재시도 및 사용자 과금 연계 |

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

### 메시지 페이지 조회

`GET /v1/seasons/{season_id}/episodes/{number}`

첫 페이지에서 반환된 `version_id`를 저장하고 이후 페이지에는 같은 `version_id`를 전달합니다.
`after_sequence`는 마지막으로 받은 메시지의 `sequence`이며 exclusive cursor입니다. `has_more=false`가
될 때까지 조회합니다. reroll로 활성 버전이 바뀌어 `VERSION_SUPERSEDED`가 반환되면 기존 페이지를
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
4. 에피소드 2 완료 후 필요하면 1:1 상대 reroll을 요청합니다.
5. 에피소드 3 generation을 요청하고 완료된 대화를 표시합니다.
6. Spring이 미니게임을 실행·판정한 뒤 3화 selection을 저장합니다.
7. 최신 revision으로 에피소드 4 generation을 요청합니다.
8. 에피소드 4 완료 후 필요하면 1:1 상대 reroll을 요청합니다.
9. 에피소드 5 generation을 요청합니다.
10. 완료 후 `GET /v1/seasons/{season_id}/report`로 최종 리포트를 조회합니다.

이후 에피소드 generation이 접수되면 이전 화 reroll은 영구 잠깁니다. 앱 화면 상태만으로 가능 여부를
결정하지 말고 최신 시즌과 API 결과를 기준으로 처리합니다.

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

## 7. reroll

`POST /v1/seasons/{season_id}/episodes/{number}/rerolls`

2·4화에서만 호출합니다.

- `expected_revision`: 최신 시즌 revision
- `expected_version_id`: 현재 활성 에피소드 version
- `partner_id`: 현재 1:1 상대와 다른 candidate ID

reroll 성공 전에는 기존 활성 version을 계속 표시할 수 있습니다. 성공하면 새 version을 처음부터
조회합니다. 다음 화 generation을 이미 요청했다면 `REROLL_LOCKED`를 사용자에게 변경 불가 상태로
표시합니다.

## 8. 오류 처리

Spring client는 최소한 다음 HTTP 상태를 구분합니다.

| HTTP | 처리 |
|---|---|
| 401 | 내부 인증/header 구성 오류; 사용자 재로그인으로 자동 치환하지 않음 |
| 404 | owner 불일치 또는 리소스 없음; 다른 사용자의 존재를 노출하지 않음 |
| 409 | revision, 진행 순서, selection, reroll 또는 version 충돌 |
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
- 이후 화 시작 후 이전 reroll을 차단하는지 테스트
- AI 내부 수치와 prompt가 앱 응답에 노출되지 않는지 contract test

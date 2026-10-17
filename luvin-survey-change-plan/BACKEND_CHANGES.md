# 백엔드 변경 명세 — 설문 채점·빵 유형 분류

분석 기준: 2026-09-26 12:03 KST 원격 branch ref 재확인. 프론트엔드 `dev`: `0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9`, 백엔드 `master`: `9c6d5c5f84e37fae5b168ab60346ff6a9a534693`. 분석 중 branch 이동 없음. 아래 근거 링크는 이동하는 branch가 아닌 해당 커밋에 고정했다.

이 문서는 **현재 코드에서 확인한 사실**과 **앞선 설문 설계를 구현하기 위한 제안**을 구분한다. 저장소 코드를 변경하거나 PR/배포/DB 변경을 수행하지 않았다. 실제 서버·DB·앱 실행은 확인하지 않았으며 정적 코드와 저장소 OpenAPI를 분석했다. 아래 신규 API와 파일은 아직 구현되지 않았다.

## 1. 결론

현재 Spring에 **버전별 설문 정의, 완결된 제출, 순수 채점/분류, 불변 결과 저장, 사용자 최신 결과 연결, AI 시즌용 성향 snapshot**을 추가한다. 새 LLM이나 별도 FastAPI 분류 endpoint는 필요 없다. 기존 설문 답변 저장 API에 합산 코드만 덧붙이면 완결성·중복·버전·재설문 문제를 해결하지 못한다.

대상 프로젝트는 Java 21 / Spring Boot 4.1.1 / JPA이며 datasource 기본값은 PostgreSQL이다. 원래 AI 서비스 계획의 Python/Pydantic/SQLAlchemy/Alembic을 이 Spring 저장소에 적용하지 않는다. [build.gradle](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/build.gradle#L1), [src/main/resources/application.yml](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/resources/application.yml#L1)

## 2. 현재 구현에서 확인한 사실

| 현재 사실 | 필요한 변경 | 근거 |
|---|---|---|
| GET /api/surveys/{questionId}, POST /api/surveys/submit만 controller에 노출 | 버전 고정 전체 정의/결과 API 신규 추가 | [src/main/java/com/luvin/survey/controller/SurveyController.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/survey/controller/SurveyController.java#L20) |
| submit은 question-option 소속을 확인하고 항목별 insert, 전체 transaction | 기존 소속 검증·rollback은 유지하되 20개 완결성/중복/같은 정의 검증 추가 | [src/main/java/com/luvin/survey/service/SurveyServiceImpl.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/survey/service/SurveyServiceImpl.java#L48) |
| SurveySubmitRequest는 Long questionId/optionId 목록, version/result 없음 | 새 API DTO는 stable string code와 definitionId 사용 | [src/main/java/com/luvin/survey/dto/SurveySubmitRequest.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/survey/dto/SurveySubmitRequest.java#L5) |
| SurveyOption.effects는 한국어 '+20' 문자열, 채점에서 사용하는 호출은 검색되지 않음 | 구조화 evidence를 가진 immutable JSON 설정으로 대체 | [src/main/java/com/luvin/survey/domain/SurveyOption.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/survey/domain/SurveyOption.java#L22), [src/main/java/com/luvin/common/config/SeedDataLoader.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/common/config/SeedDataLoader.java#L101) |
| surveyCompleted는 existsByMemberId | 답변 한 개만 있어도 true가 되므로 검증된 결과 기준으로 변경 | [src/main/java/com/luvin/user/service/UserServiceImpl.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/user/service/UserServiceImpl.java#L25) |
| User.personalityType과 updatePersonalityType은 있으나 현재 분류 호출 없음 | 새 완료 transaction에서 canonical type을 projection으로 갱신 | [src/main/java/com/luvin/user/domain/User.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/user/domain/User.java#L101) |
| seed는 surveys count==0일 때만 실행 | 기존 DB는 seed 코드 변경만으로 새 문항을 받지 못함 | [src/main/java/com/luvin/common/config/SeedDataLoader.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/common/config/SeedDataLoader.java#L37) |
| personalityQuestions()를 오늘의 질문 seed에서도 재사용 | 새 설문 도입과 daily question 변경을 분리 | [src/main/java/com/luvin/common/config/SeedDataLoader.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/common/config/SeedDataLoader.java#L92) |
| AiTraitsRequest/TraitsDto는 Integer 13개 | 소수 점수 보존 및 AI wire 계약 확인 필요 | [src/main/java/com/luvin/ai/dto/AiTraitsRequest.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/ai/dto/AiTraitsRequest.java#L3), [src/main/java/com/luvin/ai/client/dto/TraitsDto.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/ai/client/dto/TraitsDto.java#L7) |
| createSeason은 HTTP request.representative를 전달받아 AI에 사용 | 내부 DTO 주석과 실제 호출 경계가 다름. 인증된 사용자 결과를 조회하여 조립 | [src/main/java/com/luvin/ai/controller/AiSeasonController.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/ai/controller/AiSeasonController.java#L40), [src/main/java/com/luvin/ai/service/AiSeasonOrchestrationServiceImpl.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/ai/service/AiSeasonOrchestrationServiceImpl.java#L109) |
| ai_seasons는 member_id unique, 기존 시즌이 있으면 바로 반환 | 재설문이 기존 시즌 교체/새 시즌 생성으로 이어지지 않게 고정 | [src/main/java/com/luvin/ai/domain/AiSeason.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/ai/domain/AiSeason.java#L29) |
| ddl-auto:update, migration dependency/file 미확인 | 버전 관리 migration 도입과 기존 DB baseline 필요 | [src/main/resources/application.yml](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/resources/application.yml#L1), [build.gradle](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/build.gradle#L1) |

`SurveyListItemResponse`/`SurveyDetailResponse` 클래스 존재만으로 list/detail endpoint가 있다고 간주하지 않는다. 실제 Controller와 root openapi.json에는 그런 route가 없다. 운영 DB의 row 수·마이그레이션 이력·실제 배포 spec은 이번 분석 범위 밖이다.

## 3. 신규 API와 이전 API 처리

[공통 API 계약](SHARED_API_CONTRACT.md)의 추가 경로를 구현한다. 기존 `/{questionId}` Long route와 `/submit`의 DTO를 바로 바꾸지 않는다.

- GET `/api/surveys/v2/definition`: 현재 공개 중인 불변 definition과 20개 질문 반환. scoring evidence는 미노출.
- POST `/api/surveys/v2/submissions`: 서버 검증→채점→분류→원자 저장 후 결과 반환. CPU 계산으로 끝나므로 job queue/LLM 불필요.
- GET `/api/surveys/v2/results/me`: 최신 성공 결과, 없으면 envelope data=null.
- GET `/api/surveys/v2/submissions/{clientSubmissionId}`: 재시도 복구. 소유자 범위 조회.
- POST `/api/ai/seasons/from-survey`: 서버 저장 결과 기반 시즌 생성. 기존 season 생성 경로와 구분하여 점진 전환.

기존 survey submit은 새 유형·새 latest pointer를 갱신하지 않는 legacy endpoint로 분리하고 종료 시점에 410을 반환한다. 기존 API에 억지로 q01을 Long으로 변환하지 않는다. 기존 profile의 surveyCompleted 의미가 달라지는 변경은 앱 rollout과 함께 적용한다.

## 4. 코드 구조와 파일별 변경

| 위치 | 조치 |
|---|---|
| survey/controller/SurveyController.java | legacy 유지/종료 정책 명시. 새 controller는 SurveyV2Controller로 분리 권장 |
| survey/dto/* | 기존 request 유지. SurveyDefinitionResponse, SurveyV2SubmitRequest, SurveyResultResponse 신규 |
| survey/service/SurveyServiceImpl.java | legacy 범위 유지. 새 서비스로 모든 설문 동작을 조용히 갈아끼우지 않음 |
| survey/service/SurveySubmissionService.java | 신규: 인증 owner, 완결성, 멱등성, transaction, 최신 결과 교체 |
| survey/service/SurveyDefinitionService.java | 신규: 공개 정의 조회와 불변 release 조회 |
| survey/scoring/SurveyScorer.java | 신규 순수 계산: evidence 합산, 중립 prior, 반올림 정책 |
| survey/scoring/BreadTypeClassifier.java | 신규 순수 계산: 프로필 거리, 동점 순서, mixed/poorFit |
| survey/scoring/SurveyExplanationBuilder.java | 신규: 실제 선택 근거에서 최대 3개 설명 생성. 템플릿으로 구현 |
| survey/config/* + src/main/resources/surveys/<release>/ | JSON schema/DTO/loader, 앞선 survey_config.json 그대로 versioned import |
| survey/domain/* + repository/* | 아래 정의·제출·결과 신규 테이블 매핑 |
| user/domain/User.java | latestSurveyResult 관계 추가, updatePersonalityType projection 재사용 |
| user/service/UserServiceImpl.java | existsByMemberId 대신 최신 유효 결과 유무로 완료 계산 |
| user/dto/UserProfileResponse.java | nullable latestSurveyResultId, canonical personalityType, surveyCompleted 정합성 |
| common/config/SeedDataLoader.java | legacy daily seed와 새 설문 release loader 분리. 기존 count==0 조건으로 배포하지 않음 |
| common/response/ApiResponse.java 또는 신규 error DTO | survey 오류 machine code 추가. 기존 data/message shape와 호환 |
| common/exception/GlobalExceptionHandler.java | 새 validation/idempotency/version/JSON parse 오류를 400/409/410 등으로 변환 |
| ai/controller/AiSeasonController.java | from-survey endpoint 추가. 공개 API에서 임의 traits 입력을 신규 기본 경로로 쓰지 않음 |
| ai/service/AiSeasonOrchestrationServiceImpl.java | survey snapshot을 통한 입력 조립. 기존 시즌은 그대로 반환 |
| ai/service/AiSeasonStateWriter.java, ai/domain/AiSeason.java | 최초 생성 입력/resultId/version 고정, 결과 저장 시 연결 |
| ai/dto/AiTraitsRequest.java, ai/client/dto/TraitsDto.java, ai/service/AiInputValidator.java | decimal 호환 타입/검증과 관련 test 변경 |
| ai/config/AiClientConfig.java | 기존 snake_case 변환 유지; 점수 직렬화 fixture 검증 |
| analysis/service/AnalysisService.java | 아래 별도 주의: 새 분류와 기존 독립 분석 점수를 혼용하지 않음 |
| build.gradle, application.yml, src/main/resources/db/migration/ | Flyway 등 선택한 migration 도구 추가; 운영 ddl-auto validate 전환 |
| openapi.json | 실제 controller/DTO에서 새 spec export, 프론트에 동일본 전달 |

표의 Java 경로는 `src/main/java/com/luvin/` 기준이다. 새 파일명은 제안이며 기존 코드 style과 package 경계를 따른다.

## 5. 설정 원본과 배포

`reference/survey_config.json`에는 20문항, 60개 답변, 13개 core+7개 auxiliary, 8개 프로필과 세 버전이 들어 있다. 승인된 설정을 resources에 불변 release로 복사하고 canonical JSON hash를 저장한다. `status=unvalidated_editorial_prototype`인 초안임을 제품 검증 없이 production 승인으로 바꾸지 않는다.

하나의 definitionId는 surveyVersion/scoringVersion/classificationVersion/configHash를 모두 고정한다. 같은 surveyVersion에 새 scoringVersion을 적용하려면 새 definitionId를 만든다. 현재 공개 pointer는 바뀔 수 있지만 과거 definition payload는 바꾸지 않는다. 실행 중 앱은 시작 때 받은 정의로 제출한다. 구버전 accepted 기간이면 그대로 처리하고 종료되었다면 410으로 재시작을 안내한다. 서버가 제출 시점의 최신 버전으로 몰래 채점하지 않는다.

서버 시작/배포 검증: unique question/answer ID, 20×3, 20개 dimension, 각 primary의 모든 선택지 근거, weight>0, r∈{-1,0,1}, 8개 프로필, 범위/동점 순서 완결성. 잘못된 release는 활성화하지 않는다.

## 6. 채점·분류 이식 규칙

```text
S_j = Σ(value × weight)
W_j = Σweight
x_j = 50.5 + 49.5 × S_j / (2 + W_j)
```

모든 선택지의 primary weight=2, 추가 근거 weight=1. explicit 0은 W에 포함하고 키가 없으면 제외한다. 기존 effects 문자열이나 프론트의 delta 숫자를 새 evidence로 해석하지 않는다.

유형 feature 범위 [L,U]에 대한 d=max(L−x,0,x−U). 유형 거리 D=sqrt(Σimportance*d² / Σimportance). 내부 정확한 점수로 계산하고 결과 표시/AI snapshot은 소수 둘째 자리 HALF_UP. sqrt 전후에 임의 정수 반올림하지 않는다. core/auxiliary는 같은 점수식이나 AI에는 core 13개만 전달한다.

Python reference는 Fraction으로 score를 정확하게 계산하고 Decimal precision 40으로 sqrt, 거리 소수 6자리 HALF_UP으로 정렬한다. Java는 rational numerator/denominator를 유지하거나 같은 수치 결과를 내는 충분한 precision의 BigDecimal 계산을 구현한다. precision 40/HALF_UP과 경계 fixture로 parity를 검증하고, score를 먼저 NUMERIC(5,2)로 저장한 뒤 그 값으로 분류하는 방식은 금지한다. 원본 S/W도 저장하여 재계산을 보장한다.

동점 순서는 config.tie_break_order 그대로. top-two gap<3은 mixed, best distance>18은 poorFit. 임계치 비교는 표시용 반올림 전 거리로 한다. round-to-6 tie 판정과 gap 계산이 다르다는 점도 reference와 같게 유지한다. 여기의 '3/18'은 검증 전 초기값이며 confidence/probability로 이름 붙이지 않는다.

설명은 대표 유형 지표와 방향이 일치하고 50.5에서 5점보다 멀리 떨어진 근거 중 중요도×편차 상위 3개를 사용한다. 실제 answer ID를 연결한다. 기준을 만족하지 않으면 특징을 지어내지 않고 제한된 일반 설명을 반환한다.

## 7. DB 모델 제안

기존 legacy survey 테이블은 그대로 보존하고 새 versioned assessment 모델을 추가한다. 새 scheme에서 definition JSON에 질문/답변 소속이 모두 들어 있으므로 원문을 기존 Long ID 테이블로 다시 옮길 필요가 없다.

| 테이블/열 | 핵심 내용과 제약 |
|---|---|
| survey_definitions | UUID id, 세 버전, config_hash, immutable config_json JSONB, created_at; 버전 tuple unique. 상태는 active/accepted/retired 별도 열 |
| survey_submissions | UUID id, member_id FK users, definition_id FK, client_submission_id UUID, request_hash, submitted_at UTC; UNIQUE(member_id,client_submission_id) |
| survey_submission_answers | submission_id FK, question_code, answer_code; UNIQUE(submission_id,question_code). 20개 완결성·JSON 소속은 service에서 강제 |
| survey_results | UUID id, submission_id unique FK, core_scores JSONB, auxiliary_scores JSONB, evidence_stats JSONB(S/W/관찰수/충돌), distances JSONB, primary_type, secondary_type, mixed, poor_fit, tie, top_two_gap, explanation_evidence JSONB, created_at |
| users.latest_survey_result_id | nullable FK. 현재 사용자의 완료 결과만 연결. application 검증 또는 composite FK로 타 사용자 result 연결 방지 |
| users.personality_type | latest result의 primary canonical ID projection. 독립적인 진실 원천으로 사용하지 않음 |
| ai_season_creation_inputs | member_id unique, result_id FK, 고정 profile JSON, request_hash, idempotency_key, 생성 상태/시각. 최초 원격 호출 전 저장 |
| ai_seasons.survey_result_id | 최초 생성에 사용한 result FK. 생성 입력 snapshot 및 버전과 연결; 재설문 시 변경하지 않음 |

DB JSONB만으로 decimal 정확성이 보장된다고 가정하지 않는다. 결과 scores JSON에는 2자리 decimal을 쓰고 정확한 재계산은 정수 S/W와 immutable config로 한다. 판단용 거리 보관 precision도 별도로 정한다.

외부 LLM 호출 없이 한 DB transaction으로 다음을 묶는다: submissions/20 answers/result 저장 → users latest pointer 및 personalityType 갱신. 어떤 검증/계산/저장 실패에도 부분 완료를 만들지 않는다. legacy 답변은 새 latest pointer를 만들지 않는다.

멱등성과 경합: member row lock으로 사용자별 제출을 직렬화하고 UNIQUE(member_id,clientSubmissionId)로 최종 방어한다. canonical hash는 definition/version과 questionId 정렬 후 answerId 목록으로 만들며 목록 순서 차이는 동일 요청이다. 같은 key/hash면 기존 결과를 반환하고 최신 pointer를 과거로 되돌리지 않는다. 같은 key/다른 hash면 409. 다른 key 재설문은 별도 결과를 만들고 새 transaction 완료 시 latest pointer를 바꾼다. 재설문 중 UI는 기존 결과를 유지할 수 있다.

원시 응답·성향은 일반 log에 남기지 않는다. 사용자 탈퇴와 연동한 삭제/보관 정책을 추가하고 참조 중 AI snapshot의 삭제 순서를 정한다. 보관 기간 자체는 이번 코드 검토로 확정하지 않는다.

## 8. 기존 데이터와 migration

현재 `ddl-auto:update`는 versioned migration을 대신하지 않는다. 이 저장소에서는 Flyway를 기본 제안으로 두되 사용 버전과 Boot 호환 dependency는 도입 시 확인한다. 새 빈 DB와 운영 기존 schema를 각각 확인하고 baseline을 정한다. unknown DB에 baseline-on-migrate를 무조건 켜거나 운영 데이터 drop을 수행하지 않는다.

권장 순서:

1. nullable column/new table을 추가하는 expand migration. 기존 앱/API 계속 동작.
2. 신규 definition import, canonical config hash 확인. daily_questions를 수정하지 않음.
3. v2 API 배포, 새 설정은 앱 공개 전 fixture로 검증.
4. 기존 survey_answers만 있는 사용자는 'legacy 완료/새 결과 없음'으로 구분하여 새 설문 안내. 옛 questionId 순서로 새 질문을 추정하지 않음.
5. 앱 전환 후 surveyCompleted는 최신 검증 결과 기준으로 통일. 기존 personalityType이 있더라도 새 계산 provenance가 없으면 새 결과로 위장하지 않음.
6. 운영 schema 검증 후 ddl-auto validate. legacy endpoint 종료와 불필요 table 제거는 후속 contract migration이며 이번 필수 범위 아님.

기존 질문의 문구/effects를 덮어쓰면 이미 저장한 답변 의미도 변한다. 새 설문은 새 release로만 추가한다. seedPersonalitySurvey 코드를 교체하는 것만으로 기존 DB가 갱신되지 않으며, 공유 personalityQuestions 메서드 변경은 신규 환경의 오늘의 질문까지 바꾸므로 분리해야 한다.

## 9. AI 시즌 입력 연결

현재 createSeason은 existing season을 먼저 확인하고, 없으면 request.representative를 검증하여 원격 AI에 전송한다. DTO 주석에는 'Spring 내부에서 계산'이라고 적혀 있지만 controller는 HTTP body를 그대로 전달한다. 이 차이를 실제 동작으로 해결한다.

신규 from-survey 경로:

1. 기존 시즌이 있으면 기존 snapshot으로 상태를 반환한다. 재설문 결과로 수정하지 않는다.
2. 시즌 생성 입력이 이미 저장돼 있으면 그 입력과 동일한 idempotency key를 재사용한다.
3. 없으면 인증된 사용자의 requested surveyResultId가 소유자/최신 유효 결과인지 확인하고 성별 등 필요한 정보와 13개 core 점수를 로드한다.
4. 짧은 transaction으로 **원격 호출 전에** resultId·버전·profile·request hash를 ai_season_creation_inputs에 저장한다. 사용자가 직후 재설문해도 retry payload는 변하지 않는다.
5. transaction 밖에서 기존 AI client를 호출하고 응답을 저장한다. 새로운 Python 서비스는 만들지 않는다.

현재 `season:create` 멱등 key를 사용하면서 매번 최신 설문을 다시 읽으면 timeout 후 재시도 때 같은 key에 다른 payload를 보낼 수 있다. 따라서 성공 후 AiSeason에 resultId를 붙이는 것만으로는 부족하며 사전 snapshot 고정이 필요하다. member별 하나의 입력 row로 동시 생성도 직렬화한다. [src/main/java/com/luvin/ai/service/AiSeasonOrchestrationServiceImpl.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/ai/service/AiSeasonOrchestrationServiceImpl.java#L109)

`AiTraitsRequest`와 `TraitsDto`는 decimal을 담는 BigDecimal 기반으로 바꾸는 것이 기본안이다. AiInputValidator도 compareTo 및 명시적 필수/범위 검증으로 바꾸고 JSON coercion/잘못된 입력 테스트를 둔다. 문자열 숫자를 자동 허용할지 계약 없이 가정하지 않는다. 앱이 raw trait를 보낼 신규 이유는 없다.

외부 AI 서버의 최신 실제 schema는 제공된 두 저장소만으로 확정할 수 없다. 백엔드 root openapi.json은 **앱→Spring** spec이며 AI 서버 spec 증거가 아니다. AI가 소수 점수를 받는지 확인한 후 연결한다. integer만 받는다면 무음 절삭 금지: AI 전용 HALF_UP 반올림 adapter와 변환 버전을 명시적으로 결정하거나 AI 계약 변경을 먼저 한다. 내부 분류 점수/정확도는 반올림하지 않는다.

현재 AI로 나가는 CharacterProfileDto는 gender+traits만 있다. personality/adultAge/분류 메타데이터를 지원 여부 확인 없이 원격 payload에 추가하지 않는다. 분류 provenance는 우선 Spring에 저장한다. 사용자의 gender 누락 시 기본 성별을 추측하지 말고 profile 보완 오류를 반환한다.

현재 ai_seasons의 member_id unique로 시즌 완료 후에도 row가 남는다. 새 시즌 생성 기능은 이 과제에서 자동 추가하지 않는다. 재설문 후 '다음 시즌에 적용' 문구는 다중 시즌 수명주기 구현 전에는 제공하지 않는다.

## 10. 기존 분석·오늘의 질문 영향

[src/main/java/com/luvin/analysis/service/AnalysisService.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/analysis/service/AnalysisService.java#L124)의 안정성 점수는 설문 답변 개수를 가산하고 다른 점수도 MBTI/닉네임/오늘의 질문 응답 수 등을 사용한다. 이는 새 빵 분류 점수와 다르다. 신규 20답변을 legacy table에도 이중 저장해 기존 점수를 유지하는 방식은 금지한다.

필수: 새 빵 결과는 survey_results에서 읽고 기존 analysis 결과를 섞지 않는다. 기존 analysis 화면이 같은 성향 결과처럼 표시된다면 데이터 출처/설명 차이를 정리해야 한다. 기존 분석 API를 새 지표 기반으로 전면 재설계하는 것은 별도 범위이며, 근거 없는 stability 역산을 이번 변경에 추가하지 않는다.

daily question은 별도 답변/통계 기능으로 유지한다. 오늘의 질문 응답이 매일 설문 점수나 진행 중 시즌 성향을 바꾸게 하지 않는다.

## 11. 검증 기준

### 순수 계산 및 parity

reference의 all-middle, 유형별 witness, +/− 상충, 미측정 vs 명시적 0 fixture를 JUnit parameterized test로 구현한다. score 표시값·evidence W/S·유형 순위·mixed/poorFit/tie·reason IDs까지 비교한다. Decimal 경계 fixture를 추가한다. 이전 가상 응답 10,000개 결과는 심리 타당성 검증이 아니며 Java parity 검증을 대신하지 않는다.

### Controller/DB

20개 정상 제출, 19/21개, 중복 question, 타 문항 answer, 구버전, null list, 다른 owner result, 같은 key replay/conflict, 동시 제출, rollback, 최신 pointer, legacy-only profile을 검증한다. PostgreSQL JSONB/locking은 PostgreSQL 환경에서 확인하고 H2 통과만으로 대체하지 않는다.

### AI와 프로필

75.25 등의 decimal이 wire에서 유지되는지, 13개 필드명 snake_case, 보조 7개 미전송, 앱이 임의 traits를 보내도 authoritative 경로에 쓰이지 않는지, timeout+재설문 뒤 retry payload가 동일한지, 기존 시즌을 재사용하는지 확인한다. 현재 AI E2E 일부는 실제 외부 서버를 호출하므로 기본 검사에서 외부 유료 호출을 실행하지 않는다.

구현 후 기본 `./gradlew test` 및 build와 새 PostgreSQL 통합 검사를 실행한다. build.gradle의 test는 manual tag를 제외하며 manualTest는 실제 인프라용이다. 이 문서 작업에서는 Gradle 실행/의존성 설치/서버 호출을 하지 않았다.

## 12. 구현 순서

1. 불변 definition config·DTO·순수 score/classifier·golden fixtures.
2. migration + 제출/result transaction + 완료 profile projection.
3. 새 API/오류 code/OpenAPI export.
4. 프론트와 설문→결과 복구 end-to-end.
5. AI 입력 사전 snapshot/decimal 계약/from-survey API 연결.
6. 기존 데이터·old app·계정 전환·재설문 경합 검증, feature flag 활성화.

필수 설문 작업에 Redis, LLM 분류, 새 큐, 머신러닝 학습, 모든 analysis API 재작성, 기존 DB 초기화는 포함하지 않는다.

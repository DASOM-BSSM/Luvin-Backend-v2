# 설문 v2 공통 API 계약 제안

분석 기준: 2026-09-26 12:03 KST 원격 branch ref 재확인. 프론트엔드 `dev`: `0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9`, 백엔드 `master`: `9c6d5c5f84e37fae5b168ab60346ff6a9a534693`. 분석 중 branch 이동 없음. 아래 근거 링크는 이동하는 branch가 아닌 해당 커밋에 고정했다.

이 문서는 **현재 코드에서 확인한 사실**과 **앞선 설문 설계를 구현하기 위한 제안**을 구분한다. 저장소 코드를 변경하거나 PR/배포/DB 변경을 수행하지 않았다. 실제 서버·DB·앱 실행은 확인하지 않았으며 정적 코드와 저장소 OpenAPI를 분석했다. 아래 신규 API와 파일은 아직 구현되지 않았다.

## 1. 계약의 지위

아래 경로/JSON은 **추가 구현 제안**이다. 현재 저장소에 존재하는 API로 오인하지 않는다. 앱→Spring은 camelCase, config/Python reference 및 Spring→AI는 기존 snake_case 경계를 유지한다. 서버가 실제 구현 후 export한 OpenAPI로 이 문서를 검증하고 프론트 타입을 생성한다.

현재 API: GET `/api/surveys/{questionId}`는 숫자 Long ID 질문 한 개, POST `/api/surveys/submit`은 숫자 questionId/optionId 목록을 저장하고 `{message}`만 반환한다. 이 계약을 문자열로 무음 변경하지 않는다. [src/main/java/com/luvin/survey/controller/SurveyController.java](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/src/main/java/com/luvin/survey/controller/SurveyController.java#L20)

현재 root `openapi.json`은 Spring API spec이다. 신규 contract는 그 spec에 추가하고 새 commit으로 export한다. 이번 문서는 실행 서버의 `/v3/api-docs`를 호출하지 않았다. [openapi.json](https://github.com/DASOM-BSSM/Luvin-Backend-v2/blob/9c6d5c5f84e37fae5b168ab60346ff6a9a534693/openapi.json#L1)

## 2. 인증과 원칙

기존 Bearer 인증 및 `SecurityUtils.getCurrentUserId()`를 재사용한다. body에 memberId를 받지 않는다. result/submission은 인증 owner 범위로 조회한다. score, primaryType, scoringVersion/classificationVersion을 앱 입력으로 받지 않는다. 채점 버전은 definitionId로 서버가 확정한다.

모든 신규 성공 응답은 `{success:true,data:...,message:null}`. 오류에는 기존 envelope에 `code`를 추가하는 확장안을 사용한다. API 전체의 기존 오류를 동시에 바꾸는 대신 새 survey 오류 handler/DTO 또는 nullable code 필드 추가로 점진 도입한다. UI는 원문 message 대신 code를 한국어 문구로 매핑한다.

## 3. 설문 정의 조회

`GET /api/surveys/v2/definition` → 200

아래는 첫 문항만 보인 축약 예시다. 실제 응답은 모든 20문항을 포함한다.

```json
{
  "success": true,
  "data": {
    "definitionId": "22222222-2222-4222-8222-222222222222",
    "surveyVersion": "bread_survey.v1-draft",
    "scoringVersion": "evidence.v1-draft",
    "classificationVersion": "bread_profiles.v1-draft",
    "title": "나만의 반죽 만들기",
    "questionCount": 20,
    "questions": [
      {
        "questionId": "q01",
        "order": 1,
        "text": "좋아하는 사람과 더 가까워지고 싶을 때, 나는?",
        "answers": [
          {"answerId": "q01_a1", "order": 1, "text": "이번 주에 같이 할 일을 정해서 먼저 만나자고 한다."},
          {"answerId": "q01_a2", "order": 2, "text": "가볍게 만나자고 말하고, 구체적인 일정은 함께 정한다."},
          {"answerId": "q01_a3", "order": 3, "text": "상대가 먼저 만나자고 할 때까지 기다리는 편이다."}
        ]
      }
    ]
  },
  "message": null
}
```

draft 버전 문자열은 앞선 산출물의 실제 식별자다. 제품 검증 후 release 문자열을 바꾸려면 config와 definition도 새로 발급한다. 특정 기존 DB questionId=1을 q01로 간주하지 않는다. 질문의 뜻이 다르기 때문이다.

현재 정의 refetch로 진행 중 snapshot을 바꾸지 않는다. 공개할 정의가 없으면 503 `SURVEY_DEFINITION_UNAVAILABLE`. 이미 받은 accepted 정의는 current에서 내려갔더라도 제출 허용 가능하다.

## 4. 제출과 멱등성

`POST /api/surveys/v2/submissions` → 최초 201, replay 200. 응답 payload는 동일 결과.

다음은 항목 목록을 축약한 shape 예시이며 실제 제출은 20개 필요하다.

```json
{
  "definitionId": "22222222-2222-4222-8222-222222222222",
  "surveyVersion": "bread_survey.v1-draft",
  "clientSubmissionId": "33333333-3333-4333-8333-333333333333",
  "answers": [
    {"questionId": "q01", "answerId": "q01_a1"},
    {"questionId": "q02", "answerId": "q02_a2"}
  ]
}
```

입력 검증: 정확히 20개, questionId 중복 없음, 정의와 모든 문항 집합 일치, 각 answerId 소속 일치, 정의/버전 일치, null/추가 필드 거부. 알 수 없는 JSON 필드를 무시하는 전역 설정이 있다면 새 DTO에 한해 엄격 검증한다. body 크기도 제한한다.

clientSubmissionId는 앱이 최초 제출 시 생성하고 동일 재시도에 재사용한다. 서버는 UNIQUE(memberId,clientSubmissionId)와 canonical payload hash를 저장한다. 같은 키 다른 내용은 409. 목록 순서만 다른 동일 답변은 동일 요청이다. definition이 retired됐더라도 이미 성공한 key/hash replay는 먼저 기존 결과를 반환한다.

다른 키 재제출은 재설문이다. 이전 raw 답변을 수정하지 않고 새 결과를 저장한다. 앱은 응답 불명확한 상태에서 수정 제출하지 말고 먼저 복구한다. user별 transaction 직렬화로 최신 결과 결정 순서를 보장한다.

## 5. 결과 응답

아래 primaryType/문구는 shape 설명용 예시이며 위의 두 답변으로 계산한 결과가 아니다.

```json
{
  "success": true,
  "data": {
    "resultId": "44444444-4444-4444-8444-444444444444",
    "clientSubmissionId": "33333333-3333-4333-8333-333333333333",
    "definitionId": "22222222-2222-4222-8222-222222222222",
    "surveyVersion": "bread_survey.v1-draft",
    "scoringVersion": "evidence.v1-draft",
    "classificationVersion": "bread_profiles.v1-draft",
    "primaryType": "red_bean_bread",
    "primaryLabel": "팥빵",
    "secondaryType": "baguette",
    "mixed": false,
    "poorFit": false,
    "tie": false,
    "displayName": "팥빵 반죽",
    "summary": "이 설문에서는 팥빵에 가장 가깝게 나왔어요.",
    "reasonTexts": ["말보다 직접 챙기는 방식으로 마음을 전하는 답변이 두드러졌어요."],
    "completedAt": "2026-09-26T03:00:00Z"
  },
  "message": null
}
```

core/auxiliary 원시 점수, distances, answer 근거, config는 이 앱 결과 DTO에 넣지 않는다. 서버 내부 저장·AI 전달에는 필요하다. secondaryType/flags는 설명 선택에 쓸 수 있으나 확률이나 임상적 진단으로 표현하지 않는다. displayName/summary는 서버 템플릿에서 나온다. 설문 결과와 dough/personDough/baked 시뮬레이션 표시 상태는 분리한다.

`GET /api/surveys/v2/results/me` → 200, 동일 Result DTO 또는 data=null. 미완료는 에러가 아니다.

`GET /api/surveys/v2/submissions/{clientSubmissionId}` → 200, 해당 완료 Result. 존재하지 않거나 다른 사용자의 것이면 404. 응답 유실·다시 눌림 복구에 사용한다. 동기 transaction이라 외부에 partial/pending 결과를 공개하지 않는다. 조회 404 직후 최초 요청이 commit할 수 있으므로 재시도 POST도 같은 key로 한다.

## 6. 사용자 프로필

기존 GET `/api/users/me`의 bare object 응답 형식은 유지한다. `latestSurveyResultId: UUID|null`을 추가하고 `personalityType: canonical ID|null`, `surveyCompleted: boolean`의 의미를 확정한다. DB Java nullable 필드는 TS도 nullable로 반영한다.

새 기준: latestSurveyResultId가 본인의 유효한 완료 결과를 가리킬 때만 surveyCompleted=true. 기존 답변 1개 또는 미분류 legacy 응답만 있으면 false와 null로 새 설문을 안내한다. 과거 personalityType 값은 provenance가 없으면 신규 canonical 결과로 반환하지 않는다. 기존 앱 호환 기간과 완료 판단 전환 시점을 rollout에 명시한다.

현재 프론트 my-page는 personalityType을 직접 문자열로 출력한다. 새 코드값을 한국어 label로 mapping하거나 Result.primaryLabel을 사용해야 한다. 기본 profile 결과와 상세 결과 query가 일시적으로 다르면 상세 결과를 다시 조회하고 local 샘플로 맞추지 않는다.

## 7. 서버 성향으로 AI 시즌 생성

`POST /api/ai/seasons/from-survey`

```json
{"surveyResultId":"44444444-4444-4444-8444-444444444444"}
```

앱이 보내는 값은 결과 참조뿐이다. 서버는 기존 시즌 확인 → 기존 생성 입력 확인 → 본인 최신 결과 조회 → 13개 점수와 gender로 입력 고정 → 원격 AI 호출 순서로 처리한다. 최신 결과가 바뀌었고 아직 생성 입력이 없다면 409 `SURVEY_RESULT_STALE`. 이미 생성 입력이 있으면 새로운 result로 바꾸지 않고 그 입력으로 retry한다. 반환된 season status에는 additive `sourceSurveyResultId`를 포함해 앱이 현재 결과와 시즌 성향 차이를 알 수 있게 한다. legacy 시즌 provenance는 null이며 서버가 추측하지 않는다.

성공은 기존 `ApiResponse<AiSeasonStatusView>` 형태로 200을 유지한다. 기존 시즌이 있으면 그대로 반환한다. 새 시즌 정책은 별도 범위다. 새 endpoint의 input은 기존 create request와 섞지 않는다.

기존 공개 POST `/api/ai/seasons`도 생산 환경에서 임의 점수 우회 경로로 남으면 안 된다. 전환 기간에는 인증된 서버 결과로만 입력을 만들도록 수정하고, legacy raw traits는 권위 있는 값으로 사용하지 않거나 종료 후 410을 반환한다. 기존 test용 raw profile 생성은 내부 테스트 fixture에서만 사용한다.

설문 제출 transaction 안에서 원격 AI 생성까지 묶지 않는다. 설문 완료 후 실제 시즌 시작 action에서 별도로 요청한다. AI 실패 때문에 제출 결과를 rollback하거나 사용자에게 설문을 다시 풀게 하지 않는다.

## 8. 오류 계약

```json
{
  "success": false,
  "data": null,
  "message": "설문 정보를 다시 확인해 주세요.",
  "code": "SURVEY_ANSWER_INVALID"
}
```

| HTTP | code | 앱 처리 |
|---|---|---|
| 400 | SURVEY_INCOMPLETE / SURVEY_ANSWER_INVALID / SURVEY_REQUEST_INVALID | 답변/소속/버전 검증, 기존 입력 유지 |
| 401 | 기존 인증 오류 | 로그인, draft/계정 cache 정리 |
| 404 | SURVEY_SUBMISSION_NOT_FOUND / SURVEY_RESULT_NOT_FOUND | 복구 또는 결과 조회. 타 사용자 여부는 노출하지 않음 |
| 409 | SURVEY_IDEMPOTENCY_CONFLICT | 동일 ID payload 변경 금지; 상태 복구 후 새 제출 |
| 409 | SURVEY_REQUIRED / SURVEY_RESULT_STALE / PROFILE_REQUIRED | 각각 새 설문/결과 재조회/프로필 보완 |
| 410 | SURVEY_VERSION_RETIRED | 새 definition으로 재시작. 같은 번호로 답변 자동 이관 금지 |
| 503 | SURVEY_DEFINITION_UNAVAILABLE | 잠시 후 재시도 |
| 500 | SURVEY_INTERNAL_ERROR | 성공 처리 금지, 동일 key 복구/재시도 |

definitionId와 surveyVersion 불일치는 400이며 임의 최신판으로 보정하지 않는다. malformed JSON·중복 필드 등 parser 오류도 400으로 변환하고 500으로 흘리지 않는다. 현재 global handler에는 광범위 Exception→500 경로가 있으므로 명시적 처리 추가가 필요하다.

## 9. 공동 완료 조건

백엔드에서 새 결과를 생성하고 앱에서 표시한 다음 재시작해 같은 결과를 조회할 수 있어야 한다. 네트워크 재시도·다른 계정·구버전·재설문·AI 생성 실패/재시도가 결과를 바꾸거나 다른 사용자 데이터와 섞지 않아야 한다. 서버 계산은 reference golden fixtures와 일치해야 한다. 앱/서버 모두 같은 export된 OpenAPI로 최종 확인한다.

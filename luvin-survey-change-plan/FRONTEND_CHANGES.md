# 프론트엔드 변경 명세 — 설문 v2 연결

분석 기준: 2026-09-26 12:03 KST 원격 branch ref 재확인. 프론트엔드 `dev`: `0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9`, 백엔드 `master`: `9c6d5c5f84e37fae5b168ab60346ff6a9a534693`. 분석 중 branch 이동 없음. 아래 근거 링크는 이동하는 branch가 아닌 해당 커밋에 고정했다.

이 문서는 **현재 코드에서 확인한 사실**과 **앞선 설문 설계를 구현하기 위한 제안**을 구분한다. 저장소 코드를 변경하거나 PR/배포/DB 변경을 수행하지 않았다. 실제 서버·DB·앱 실행은 확인하지 않았으며 정적 코드와 저장소 OpenAPI를 분석했다. 아래 신규 API와 파일은 아직 구현되지 않았다.

## 1. 결론

기존 20문항 화면과 빵 이미지 자산은 재사용한다. 앱의 +20/−20 합산은 최종 채점에서 제거하고 **서버가 배포하는 문항을 표시 → 답변 ID 제출 → 서버가 확정한 빵 유형을 표시**하는 흐름으로 바꾼다. 점수식·유형별 범위·보조 지표를 앱에 이중 구현하지 않는다.

앱의 주된 변경은 ① 문항 API 연결 ② 버전과 답변 ID 저장 ③ 마지막 답변 제출 ④ 내 결과 복구/표시 ⑤ AI 시즌 생성 입력 변경이다. 기존 Expo/React Native 프로젝트를 재생성하거나 새로운 HTTP client를 만들 필요가 없다.

## 2. 현재 구현에서 확인한 차이

| 현재 사실 | 변경이 필요한 이유 | 근거 |
|---|---|---|
| 문항 20개가 TypeScript 상수이며 보기 ID는 a/b/c, 문항 번호는 1~20이다 | 새 q01/q01_a1과 기존 DB Long ID는 서로 호환되지 않음 | [src/features/survey/constants/questions.ts](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/src/features/survey/constants/questions.ts#L10), [src/features/survey/types/index.ts](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/src/features/survey/types/index.ts#L24) |
| 13개 점수를 0에서 시작해 deltas를 더한다. 무응답/잘못된 보기 ID는 건너뜀 | 근거 가중 평균·7개 보조 지표·분류식이 없고 1~100 점수도 아님 | [src/features/survey/utils/scoring.ts](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/src/features/survey/utils/scoring.ts#L16) |
| 마지막 문항에서 제출/결과 이동 TODO 후 return | 화면만으로 완료할 수 없음 | [src/app/survey/questions.tsx](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/src/app/survey/questions.tsx#L47) |
| 진행은 메모리 Zustand, 재시작 시 초기화 | definition 버전·제출 재시도 키를 저장할 자리가 필요 | [src/features/survey/store/survey-store.ts](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/src/features/survey/store/survey-store.ts#L13) |
| 내 빵은 전역 MMKV bread.profile에서 로드 | 서버 결과 및 로그인 계정과 불일치 가능 | [src/features/bread/store/bread-store.ts](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/src/features/bread/store/bread-store.ts#L34) |
| 내 성향 설명과 추천 영상은 소금빵 기준 상수 | 유형만 바꿔도 다른 유형에 소금빵 설명이 붙음 | [src/app/bread.tsx](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/src/app/bread.tsx#L15) |
| 홈은 profile!=null로 설문/시즌 진입 판정 | stale local data가 서버의 미완료 상태를 가릴 수 있음 | [src/app/index.tsx](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/src/app/index.tsx#L33) |
| createSeason 함수는 있지만 src 안에서 호출하는 곳은 검색되지 않음 | 설문 완료와 시즌 시작 사이에 실제 연결 필요 | [src/features/inferno/api/ai-season.ts](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/src/features/inferno/api/ai-season.ts#L13) |
| axios·TanStack Query가 설치돼 있고 http-client.ts 존재 | AGENTS의 일부 '미설치' 설명보다 실제 package/source가 최신 | [package.json](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/package.json#L1), [src/lib/http-client.ts](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/src/lib/http-client.ts#L15) |

## 3. 확정할 클라이언트 계약

[공통 API 계약](SHARED_API_CONTRACT.md)을 따른다. 경로와 JSON은 신규 제안이며 기존 API에 바로 요청하면 안 된다.

- GET `/api/surveys/v2/definition`: 표시용 20문항과 불변 definitionId, surveyVersion, scoringVersion, classificationVersion.
- POST `/api/surveys/v2/submissions`: definitionId, surveyVersion, clientSubmissionId, `[{questionId,answerId}]` 20개.
- GET `/api/surveys/v2/results/me`: 현재 로그인 사용자의 최신 완료 결과 또는 null.
- GET `/api/surveys/v2/submissions/{clientSubmissionId}`: 응답 유실 후 동일 제출 결과 복구. 본인만 조회.

성공 envelope는 새 API에서 `{success,data,message}`로 통일한다. 기존 `/api/users/me`는 bare object이므로 공통 interceptor에서 모든 응답을 무조건 data.data로 바꾸지 않는다.

앱은 계산용 config를 받지 않는다. definition은 문항/답변 ID·문구·순서만 노출한다. `primary_dimension`, evidence, profile 범위는 서버 전용이다.

## 4. 파일별 변경 목록

경로는 이 저장소 기준이며 '신규'는 구현할 파일 제안이다.

| 파일 | 조치 | 구체적인 책임 |
|---|---|---|
| src/features/survey/constants/questions.ts | 대체 | 운영 문항 source를 API로 이동. 옛 문항/가중치는 운영에서 제거하고 필요 시 legacy fixture만 유지 |
| src/features/survey/constants/traits.ts | 정리 | 화면에서 쓰지 않으면 삭제 후보. raw 점수 계산 dependency 제거 |
| src/features/survey/types/index.ts | 수정 | QuestionId/AnswerId를 string으로, answers를 Record<string,string>으로. 표시 순서와 ID 분리 |
| src/features/survey/store/survey-store.ts | 수정 | definitionId/버전/currentIndex/answers/clientSubmissionId/frozenPayload를 메모리에서 관리. reset 시 모두 초기화 |
| src/features/survey/utils/scoring.ts | 분리 | calculateTraitScores를 운영 경로에서 제거. 완성도 검증/진행률만 definition 기반 순수 함수로 이전 |
| src/features/survey/api/survey.ts | 신규 | 기존 httpClient로 definition/submit/result/recovery 호출 |
| src/features/survey/api/query-keys.ts | 신규 | 로그인 memberId·definitionId를 포함하는 key factory |
| src/features/survey/hooks/use-survey-definition.ts | 신규 | 진입 시 정의 조회, 재시작 전까지 해당 snapshot 고정 |
| src/features/survey/hooks/use-submit-survey.ts | 신규 | 중복 제출 차단, 제출 payload 고정, 결과 cache 및 사용자 profile invalidate |
| src/features/survey/hooks/use-my-survey-result.ts | 신규 | 결과 재조회·앱 재시작·다른 기기 복구의 기준 |
| src/features/survey/utils/answers.ts | 신규 | 모든 문항 응답 여부뿐 아니라 answerId의 소속까지 검사 |
| src/features/survey/utils/map-bread-result.ts | 신규 | canonical 유형을 기존 BreadType과 표시 DTO로 변환 |
| src/app/survey/index.tsx | 수정 | 문항 로딩 완료 시 시작 가능, 오류 재시도, 이전 결과/재설문 구분 |
| src/app/survey/questions.tsx | 수정 | 배열 위치로 화면 표시하되 ID로 저장, 최종 선택 포함한 payload 제출, 성공 후 /bread 이동 |
| src/features/survey/components/survey-option-card.tsx | 수정 | selected/disabled/pending와 accessibilityState 지원. 선택 시 바로 다음 문항 UX 유지 |
| src/features/bread/store/bread-store.ts | 축소/폐기 | MMKV를 서버 결과의 원천으로 사용하지 않음. 소비자를 query-derived profile hook으로 이동 |
| src/features/bread/hooks/use-bread-profile.ts | 신규 | 서버 결과를 BreadProfile로 변환. 로딩/미완료/실패를 구분 |
| src/app/bread.tsx | 수정 | server reasonTexts와 타입별 label 표시. 고정 BREAD_TRAITS 제거, 추천 영상은 연동 전 숨기거나 일반 콘텐츠로 명시 |
| src/app/index.tsx, src/app/my-page/index.tsx | 수정 | 같은 결과 hook 사용, 로딩 중 '설문 전'으로 오판하지 않음, canonical personalityType을 그대로 배지로 노출하지 않음 |
| src/features/inferno/hooks/use-inferno-conversation.ts | 수정 | 내 profile은 위 hook/시즌 snapshot에서 공급 |
| src/app/inferno/ep5.tsx | 수정 | SAMPLE_BREAD_PROFILE fallback 제거. 본인 결과/시즌 snapshot 없는 상태를 복구/오류로 처리 |
| src/features/user/types/index.ts | 수정 | nullable profile 필드와 latestSurveyResultId 등 실제 OpenAPI 반영 |
| src/features/inferno/api/ai-season.ts, ai-season-types.ts | 수정 | 앱이 traits를 조립하는 신규 흐름 제거. 서버가 결과를 읽는 새 create API 사용 |
| src/features/inferno/hooks/use-create-season.ts | 신규 | 실제 시즌 시작 action에 연결, AI 실패는 설문 완료와 분리 |
| src/features/auth/hooks/use-logout.ts, src/lib/http-client.ts | 수정 | 로그아웃/401에서 설문 draft·옛 bread.profile과 bridge store도 정리 |
| docs/api/openapi.json, src/types/api-generated.ts | 신규 | 백엔드 실제 새 OpenAPI 반입 및 타입 생성 체계 마련 |

## 5. 화면 상태와 제출 로직

1. 인증 완료 후 definition을 조회한다. 배열이 비었거나 문항 수/보기 수가 계약에 맞지 않으면 시작하지 않는다.
2. 시작 시 definition snapshot을 고정한다. query refetch가 진행 중 문구/답변 ID를 바꾸면 안 된다.
3. 답변은 questionId→answerId로 저장한다. currentIndex는 화면 전용이며 1부터 표시한다.
4. 마지막 선택에서는 stale closure의 answers를 제출하지 않는다. `nextAnswers={...answers,[questionId]:answerId}`를 만들어 저장과 제출에 같은 객체를 사용한다.
5. 최종 payload를 동결하고 clientSubmissionId를 한 번 발급한다. 빠른 연속 탭과 back navigation으로 제출이 중복되지 않도록 잠근다.
6. pending 동안 보기와 해당 제출 action을 비활성화한다. 성공 후 결과 cache에 반영하고 user profile을 invalidate한 뒤 `/bread`로 이동한다.
7. 서버 응답이 오기 전에는 SAMPLE_BREAD_PROFILE로 성공 처리하지 않는다.
8. 네트워크 실패 시 답변을 유지하고 같은 ID/같은 payload로 재시도한다. 서버가 저장했는지 모르면 submission 조회로 확인한다. 그 사이 수정은 우선 복구 후 명시적 새 설문으로 처리한다.
9. 앱 종료 시 draft는 현재처럼 메모리에서 사라져도 된다. 완료 결과는 로그인 후 server result GET으로 복구한다. 설문 이어하기의 영속 저장은 별도 제품 범위다.
10. 401이면 현재 저장소 동작대로 로그인 화면으로 이동한다. 없는 refresh token API를 새로 가정하지 않는다. 계정 전환 시 이전 설문 답변이 보이지 않아야 한다.

새 UUID 발급에 추가 패키지가 필요한지 확인한다. 현재 미설치 패키지를 임의 추가하지 않고, 프로젝트가 승인한 방식으로 clientSubmissionId를 생성한다. UUID는 인증 수단이 아니며 서버는 memberId scope로 소유권을 검사한다.

## 6. 유형명과 에셋 매핑

기존 [src/assets/images/BreadCharacter.tsx](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/src/assets/images/BreadCharacter.tsx#L6)에 8개 유형 자산이 있으므로 새 이미지 제작은 필요 없다.

| 서버 canonical ID | UI BreadType | 표시명 |
|---|---|---|
| cream_bread | cream | 크림빵 |
| red_bean_bread | redbean | 팥빵 |
| salt_bread | salt | 소금빵 |
| pretzel | pretzel | 프레첼 |
| donut | donut | 도넛 |
| baguette | baguette | 바게트 |
| madeleine | madeleine | 마들렌 |
| castella | castella | 카스테라 |

알 수 없는 값은 salt로 fallback하지 않고 '결과를 불러올 수 없음'으로 처리한다. primaryType은 canonical code, primaryLabel은 한국어 이름이다. 기존 BreadProfile.name/description은 서버 표시 문구에서 만든다. 분류 결과와 빵의 dough/baked 상태는 별개이며 설문 완료만으로 baked로 바꾸지 않는다.

mixed/poorFit는 심리적 '신뢰도'가 아니다. 확률 게이지 대신 서버의 제한된 설명을 표시한다. 원시 점수·유형별 거리·20개 근거 지표는 화면/일반 analytics에 노출하지 않는다.

## 7. 기존 필드명 정리

앱에서 최종 점수를 만들지 않으므로 기존 SurveyTrait를 AI request로 직접 변환하는 코드가 필요하지 않게 된다. 남아 있는 타입/개발 fixture를 정리할 때 다음 차이를 놓치지 않는다.

| 기존 프론트 설문 key | 서버/AI camelCase | 기준 config key |
|---|---|---|
| emotionSuppression | emotionalSuppression | emotional_suppression |
| attentionFrequency | interestExpressionFrequency | interest_expression_frequency |
| energyDependence | relationshipEnergyDependence | relationship_energy_dependence |
| emotionalSynchrony | emotionalAttunement | emotional_attunement |
| realityPriority | practicalPriority | practical_priority |

나머지 8개도 스키마 기준으로 생성하고 문자열 변환으로 추측하지 않는다. 프론트 `AiCharacterProfileRequest`의 adultAge/personality는 현재 백엔드 DTO에 없으므로 새 server-derived 시즌 API를 만들 때 함께 정리한다.

## 8. 캐시와 사용자 분리

현재 logout은 auth/query cache만 비우고 bread/survey store는 비우지 않는다([src/features/auth/hooks/use-logout.ts](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/src/features/auth/hooks/use-logout.ts#L15)). HTTP 401도 동일한 문제가 있다([src/lib/http-client.ts](https://github.com/DASOM-BSSM/Luvin-Frontend-v2/blob/0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9/src/lib/http-client.ts#L15)). 권장안은 `useBreadStore`의 영속 source를 제거하고 TanStack Query의 서버 결과로 통합하는 것이다. 전환 중 store가 남으면 로그인 memberId와 resultId를 포함한 bridge만 허용한다.

배포 시 기존 전역 MMKV `bread.profile`은 삭제한다. 이름이 같다고 현재 로그인 사용자 것이라고 인정하지 않는다. 설문 답변과 raw scores는 새로 MMKV에 저장하지 않는다. query key 사용자 분리 외에도 로그아웃 시 draft 초기화를 수행한다.

## 9. OpenAPI와 저장소 규칙

AGENTS.md §19는 OpenAPI를 기준으로 타입 생성하도록 하지만 현재 `docs/api/openapi.json`, `src/types/api-generated.ts`, generate script/openapi-typescript는 확인되지 않았다. 백엔드 루트 `openapi.json`이 존재하며 현재 survey/AI controller와 대조했다. 개발 시에는 새 API를 구현한 백엔드가 export한 spec을 전달받아 반입한다. 이 문서의 제안 JSON을 운영 spec으로 위장하지 않는다.

현재 HTTP client 파일명은 `src/lib/http-client.ts`다. AGENTS의 목표 파일명 `api-client.ts`를 따라 두 번째 axios instance를 만들지 않는다. 사용자의 이번 요청은 두 repo를 직접 확인하는 것이므로 코드/API 비교를 수행했으며, 이 결과를 문서화했다.

기존 pnpm·NativeWind·TanStack Query 관례를 유지한다. 선택됨/제출 중/오류 상태의 시각 디자인은 구현 전에 Figma에서 확인한다. 설문 문구 변경을 위해 전체 디자인을 재작성할 필요는 없다.

## 10. 수락 기준과 실행 검증

- 20개 문항/각 3개 답변이 서버 정의와 같고, 답변 ID로 제출된다.
- 뒤로가기 후 기존 선택 표시, 답변 변경, 마지막 답변 포함, 연속 탭 차단.
- 실패 시 같은 submissionId로 재시도하고 중복 결과가 생기지 않는다.
- 앱 재시작/재로그인 후 서버 결과 복구. A계정 로그아웃 후 B계정에서 A의 빵/답변이 보이지 않는다.
- 8개 canonical 유형 모두 기존 에셋에 올바르게 매핑되고 소금빵 상수가 다른 결과에 남지 않는다.
- null 결과/로딩/오류/구버전 종료/혼합 결과를 구분한다.
- 설문 성공 후 AI 시즌 생성 실패 시 설문을 다시 요구하지 않는다.
- 재설문해도 기존 시즌의 대변 캐릭터는 이전 snapshot 유지.

구현 후 `pnpm tsc --noEmit` 및 iOS/Android 수동 검증을 수행한다. 현재 AGENTS는 자동 테스트 프레임워크 도입을 별도 결정으로 두고 있고 lint도 초기 실행 시 설치를 유발할 수 있으므로, 이 문서 작업에서는 설치/실행하지 않았다. 새 deps 없이 가능한 순수 mapping fixture 확인을 우선하고, 실제 기기 검증 여부를 명시한다.

## 11. 구현 순서

1. 백엔드와 [공통 API 계약](SHARED_API_CONTRACT.md) 확정, 실제 OpenAPI 전달.
2. definition/result API + hook + store ID 전환.
3. 마지막 답변 제출, 복구, 오류 처리.
4. bread/home/my-page 공통 결과 source 전환과 샘플 제거.
5. 서버 결과 기반 시즌 시작 API 연결, 기존 시즌 snapshot 표시.
6. 계정 전환·구버전·8개 유형·iOS/Android 확인.

백엔드가 배포되기 전 feature flag로 새 설문 경로를 노출하지 않는다. 프론트만 먼저 배포하고 없는 API를 호출하도록 만들지 않는다.

# Luvin 설문·빵 유형 분류 도입 변경 계획

분석 기준: 2026-09-26 12:03 KST 원격 branch ref 재확인. 프론트엔드 `dev`: `0d4a65ce224e4076fd1dd6f3dfb15798fea0c7f9`, 백엔드 `master`: `9c6d5c5f84e37fae5b168ab60346ff6a9a534693`. 분석 중 branch 이동 없음. 아래 근거 링크는 이동하는 branch가 아닌 해당 커밋에 고정했다.

이 문서는 **현재 코드에서 확인한 사실**과 **앞선 설문 설계를 구현하기 위한 제안**을 구분한다. 저장소 코드를 변경하거나 PR/배포/DB 변경을 수행하지 않았다. 실제 서버·DB·앱 실행은 확인하지 않았으며 정적 코드와 저장소 OpenAPI를 분석했다. 아래 신규 API와 파일은 아직 구현되지 않았다.

## 문서

1. [프론트엔드 변경사항](FRONTEND_CHANGES.md): 현재 파일별 변경, 화면 상태, API hook, 결과 복구, 계정 분리, 8개 에셋 매핑.
2. [백엔드 변경사항](BACKEND_CHANGES.md): 완결된 제출, 채점/분류, DB·버전·멱등성, 기존 기록 전환, AI snapshot.
3. [공통 API 계약](SHARED_API_CONTRACT.md): 양쪽에서 함께 확정할 신규 경로·JSON·오류·롤아웃.
4. [이전 설문/분류 명세](reference/SURVEY_AND_CLASSIFICATION.md), [원본 설정](reference/survey_config.json), [Python 참조 구현](reference/classifier.py).
5. [구현 이식용 golden fixtures](reference/golden_fixtures.json): 기존 reference 실행으로 만든 입력/기대 출력 73개. 구현의 비교 기준이며 심리 타당성 검증 자료가 아니다.

## 우선순위

- P0: 서버 definition/완결성/채점/결과 저장, 앱 제출·결과 연결.
- P0: owner 격리·재시도 멱등성·실패 복구·surveyCompleted 의미 수정.
- P0(AI 연결): 서버 결과로 season 입력 고정, decimal wire 확인, 앱 raw traits 우회 제거.
- P1: existing profile/analysis 표시 정합성·legacy API 종료·선택 상태 UX 정리.
- 별도 결정: 설문 영속 이어하기, 신규 UI 테스트 프레임워크, 실제 심리 타당성 검증, 다중 시즌 정책.

## 검토 범위와 한계

지정 branch를 shallow clone하여 코드와 OpenAPI를 읽었고 원격 ref를 다시 확인했다. 두 저장소 파일은 수정하지 않았다. 앱 실행/Gradle 테스트/production DB 조회/외부 AI 호출/Figma 검토는 수행하지 않았다. 문서의 코드 링크와 경로, reference fixture 생성은 로컬 검증했다. 신규 UI의 실제 디자인과 AI 서버의 decimal 수용 여부는 구현 전 확인 대상이다.

문서는 현재 코드 기준이며 인용한 커밋 이후 변경은 포함하지 않는다. 문서에서 '신규/제안'한 endpoint를 이미 존재한다고 가정하면 안 된다.

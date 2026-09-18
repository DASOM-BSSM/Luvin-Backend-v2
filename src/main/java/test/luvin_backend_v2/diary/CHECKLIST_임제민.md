# 체크리스트 — 임제민

`diary` / `diaryroom` 패키지 개발 일정 (9/18 ~ 9/30). 모듈 서브구조는 `AGENT.md` §8 기준(`controller / service / repository / domain / dto`).

---

## 📁 diary 패키지

- [ ] **9/18(금) 일기 공개범위 설정 (visibility)**
  - [ ] Entity
  - [ ] Repository
  - [ ] Service
  - [ ] Controller
- [ ] **9/19(토) 일기 수정 API** (PATCH/PUT) — Spring Security로 작성자 본인 체크
- [ ] **9/19(토) 일기 삭제 API** (DELETE) — Spring Security로 작성자 본인 체크
- [ ] **9/21(월) 공감 등록 API** (POST) — `@Transactional` + 동시성 처리(비관적 락 or 원자적 UPDATE 쿼리)
- [ ] **9/21(월) 공감 취소 API** (DELETE) — `@Transactional` + 동시성 처리
- [ ] **9/25(금) 공유 일기 조회(커뮤니티) API** — 조인/DTO 프로젝션

## 📁 diaryroom 패키지

- [ ] **9/22(화) 공유방 참여 API** (POST)
- [ ] **9/22(화) 공유방 나가기 API** (DELETE)
- [ ] **9/23(수) 공유방 멤버 추가 API** — 방장 권한 체크(`@PreAuthorize` 등)
- [ ] **9/23(수) 공유방 멤버 강퇴(kick) API** — 방장 권한 체크(`@PreAuthorize` 등)
- [ ] **9/24(목) 공유방 일기 조회 API** — QueryDSL/JPQL 조인, `likeCount`/`commentCount` 서브쿼리 또는 DTO 프로젝션

## 🔧 공통 작업

- [ ] **9/26(토)** 코드리뷰, 진행상황 크로스체크, 밀린 작업 처리
- [ ] **9/28(월)** 권한 검증 전체 점검, 응답 DTO 필드 일관성 점검(`isMine`, `likeCount` 등)
- [ ] **9/29(화)** 마지막 통합 정리, 미완성분 최종 처리, Swagger 문서 최종 정리
- [ ] **9/30(수)** 단위테스트 + 통합테스트 전체 API 시나리오 테스트 / 발견된 버그 즉시 수정

---

담당 범위 밖(김리원 담당) 체크리스트는 [CHECKLIST_김리원.md](./CHECKLIST_김리원.md) 참고.

# 체크리스트 — 김리원

`diary` / `diaryroom` 패키지 개발 일정 (9/18 ~ 9/30). 모듈 서브구조는 `AGENT.md` §8 기준(`controller / service / repository / domain / dto`).

---

## 📁 diary 패키지

- [ ] **9/18(금) 일기 작성 API** (POST)
  - [ ] DTO (Request/Response)
  - [ ] Service
  - [ ] Controller
- [ ] **9/18(금) 일기 목록조회 API** (GET)
  - [ ] Service
  - [ ] Controller
- [ ] **9/19(토) 일기 상세조회 API** (GET)
  - [ ] Service
  - [ ] Controller
- [ ] **9/22(화) 댓글 작성 API** (POST)
- [ ] **9/22(화) 댓글 조회 API** (GET)
- [ ] **9/23(수) 댓글 수정 API** (PATCH/PUT)
- [ ] **9/23(수) 댓글 삭제 API** (DELETE)
- [ ] **9/25(금) 정렬(`?sort=`) 파라미터 적용** — 일기 목록/댓글 조회 등 diary 쪽 전체

## 📁 diaryroom 패키지

- [ ] **9/19(토) 공유방 생성 API** (POST)
- [ ] **9/19(토) 공유방 목록조회 API** (GET)
- [ ] **9/21(월) 공유방 수정 API** (PATCH/PUT)
- [ ] **9/21(월) 공유방 삭제 API** (DELETE)
- [ ] **9/24(목) 공유방 멤버 목록조회 API** (GET)
- [ ] **9/25(금) 정렬(`?sort=`) 파라미터 적용** — diaryroom 쪽 전체

## 🔧 공통 작업

- [ ] **9/26(토)** 코드리뷰, 진행상황 크로스체크, 밀린 작업 처리
- [ ] **9/28(월)** 남은 버그/미완성 API 마무리
- [ ] **9/29(화)** Swagger 문서 최종 정리
- [ ] **9/30(수)** 발견된 버그 즉시 수정

---

담당 범위 밖(임제민 담당) 체크리스트는 [CHECKLIST_임제민.md](./CHECKLIST_임제민.md) 참고.

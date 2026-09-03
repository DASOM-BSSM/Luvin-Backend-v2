# Git Branch Strategy

## 브랜치 전략

`master` 브랜치에서는 직접 작업하지 않습니다.

모든 기능 개발은 새로운 브랜치를 생성한 후 진행하며, 작업 완료 후 Pull Request(PR)를 통해 병합합니다.

---

## 브랜치 생성 순서

### 1. 최신 코드 가져오기

```bash
git checkout master
git pull origin master
```

### 2. 새로운 브랜치 생성

```bash
git checkout -b feat/#12
```

예시

```text
feat/#12
fix/#15
refactor/#21
docs/#30
```

> `#번호`는 GitHub Issue 번호를 의미합니다.

---

## 브랜치 타입

| Type | Description |
|------|-------------|
| **feat** | 새로운 기능 추가 |
| **fix** | 버그 수정 |
| **refactor** | 코드 리팩토링 |
| **test** | 테스트 코드 작성 |
| **docs** | 문서 수정 |
| **chore** | 설정 변경 및 기타 작업 |
| **build** | 빌드 관련 설정 변경 |
| **delete** | 코드 및 파일 삭제 |

---

## 커밋 컨벤션

### 형식

```
타입(#Issue번호) :: 변경 사항 요약
```

### 예시

```
feat(#12) :: 회원가입 API 구현
fix(#15) :: JWT 인증 오류 수정
refactor(#21) :: UserService 리팩토링
docs(#30) :: README 수정
```

---

## Pull Request 제목

### 형식

```
[타입] #Issue번호 : 작업 내용
```

### 예시

```
[Feat] #12 : 회원가입 API 구현
[Fix] #15 : JWT 인증 오류 수정
[Refactor] #21 : UserService 리팩토링
```

---

## 작업 순서

### 1. GitHub Issue 생성

예시

```
#12 회원가입 API 구현
```

---

### 2. 최신 코드 가져오기

```bash
git checkout master
git pull origin master
```

---

### 3. 브랜치 생성

```bash
git checkout -b feat/#12
```

---

### 4. 기능 개발

---

### 5. 변경 사항 확인

```bash
git status
```

---

### 6. 변경 사항 추가

```bash
git add .
```

---

### 7. 커밋

```bash
git commit -m "feat(#12) :: 회원가입 API 구현"
```

---

### 8. 원격 저장소 Push

```bash
git push -u origin feat/#12
```

---

### 9. Pull Request 생성

- Base Branch : `master`
- Compare Branch : `feat/#12`

코드 리뷰 후 Merge를 진행합니다.
# 스테이징 배포

`.github/workflows/deploy-staging.yml`이 스테이징 배포를 담당한다. `DASOM-BSSM/luvin-infra`
`staging/`의 Terraform이 만드는 ECS 클러스터(`luvin-staging`)·서비스(`luvin-staging-backend`)·
ECR(`luvin-staging-backend`)·ALB 타겟그룹을 그대로 사용하며, 이 워크플로는 그 인프라를 재생성하지
않고 컨테이너 이미지만 새 revision으로 바꾼다.

## 파이프라인

- **PR**: `verify` job만 실행 — 테스트(`./gradlew test`), 빌드(`bootJar`, `docker build`). 배포하지 않는다.
- **`master` push**: `verify` 통과 후 `deploy` job 실행 — 이미지를 커밋 SHA로 ECR에 push하고, digest를
  구해 불변 참조(`repo@sha256:...`)로 배포한다. 태그만으로 배포하지 않는다(태그는 나중에 다른 이미지를
  가리키도록 덮어써질 수 있어 불변성이 없다).
- **`workflow_dispatch`**: 수동 재실행. `master`의 최신 코드로 verify→deploy를 다시 돈다.
- `concurrency: backend-staging-deploy`로 동시 배포를 막는다(새 실행이 기존 배포를 취소하지 않고
  대기한다).

## 배포 스텝(`infra/deploy/deploy-backend-staging.sh`)

1. 현재 서비스의 실행 중 task definition을 그대로 읽어와서 `backend` 컨테이너의 `image` 필드만
   바꾸고 새 revision으로 등록한다. 환경변수, Secrets Manager 참조, IAM role, 로그 설정, 포트 8080,
   그 외 모든 필드는 그대로 유지된다(Service Connect 설정은 태스크 정의가 아니라 ECS 서비스 리소스
   자체에 있어서 이 스크립트가 건드리지 않는 한 자동으로 보존된다).
2. `aws ecs update-service`로 새 revision을 적용하고 `aws ecs wait services-stable`로 안정화를 기다린다.
3. 서비스의 최종 task definition이 방금 등록한 revision과 실제로 일치하는지 확인한다 — 일치하지
   않으면(예: 배포 circuit breaker가 자동 롤백한 경우) 실패 처리한다.
4. ALB 타겟그룹(`luvin-staging-backend`)의 타겟 헬스를 직접 조회해 `healthy`가 될 때까지 최대
   150초 기다리고, 안 되면 실패 처리한다.

인프라 쪽(`luvin-infra`)에는 `aws_ecs_service.backend`에 `deployment_circuit_breaker { enable = true,
rollback = true }`를 켜 뒀다 — 새 태스크가 계속 실패하면 ECS가 자동으로 이전 revision으로 롤백한다.

## DB 마이그레이션

**지금은 전용 마이그레이션 도구가 없다.** `spring.jpa.hibernate.ddl-auto=update`가 앱 기동 시
스키마를 자동으로 맞추는 방식이라, 실패를 격리해서 배포를 막을 수 있는 별도 단계가 존재하지 않는다.
스키마 변경이 실패하면 앱이 기동 실패하고, 그 경우 위 3번 단계(task definition 불일치) 또는 4번
단계(ALB 헬스체크)에서 배포 실패로 잡히긴 하지만, "마이그레이션 따로 실행 후 실패하면 서비스 갱신
자체를 하지 않는다"는 격리는 없다.

Flyway 등 전용 도구가 추가되면, 서비스 갱신 전에 아래 순서를 추가해야 한다 (AI 저장소
`infra/deploy/deploy-ai-staging.sh`의 `register_with_image` + `run-task` +
`aws ecs wait tasks-stopped` + exit code 확인 패턴을 그대로 따르면 된다):

1. 새 task definition을 먼저 등록한다(현재 스크립트의 `register_with_image`와 동일).
2. 그 task definition으로 마이그레이션 명령을 override한 일회성 `aws ecs run-task`를 실행한다.
3. `aws ecs wait tasks-stopped`로 끝날 때까지 기다리고 컨테이너 exit code를 확인한다.
4. exit code가 0이 아니면 여기서 즉시 실패 처리하고 **서비스는 갱신하지 않는다.**
5. 성공했을 때만 3번 이후 `aws ecs update-service`를 실행한다(지금 스크립트의 나머지 부분).

### 하위 호환 마이그레이션 원칙 (자동 롤백을 가정하지 않는다)

배포 실패 시 ECS는 이전 task definition으로 롤백하지만, **DB 스키마는 자동으로 롤백되지 않는다.**
그래서 모든 마이그레이션은 "새 스키마 + 이전 코드"와 "새 스키마 + 새 코드"가 동시에 성립하도록
작성해야 한다(expand-contract):

- 컬럼/테이블 추가는 기본적으로 안전하다(이전 코드는 새 컬럼을 모를 뿐 무시한다). 새 컬럼은
  `NOT NULL`로 바로 추가하지 말고, nullable로 추가하거나 default를 주고, 이전 코드가 채우지 않는
  경우를 감안한다.
- 컬럼/테이블 삭제나 rename은 그 컬럼을 참조하는 코드가 완전히 사라진 **다음 배포**에서만 한다.
  같은 배포에서 "코드도 바꾸고 컬럼도 지우는" 것은 롤백 시 이전 코드가 없는 컬럼을 참조하게 되어
  금지한다.
- 배포 실패로 롤백이 필요하면 기본적으로 forward-fix(문제를 고친 새 커밋을 다시 배포)를 쓴다.
  DB를 이전 시점으로 되돌리는 건 별도 백업 복구 절차이지, 이 파이프라인이 자동으로 하는 일이
  아니다.

## GitHub Actions 권한 (OIDC)

`luvin-infra` `staging/backend_github_actions.tf`의 `luvin-staging-backend-github-actions-deploy`
role만 사용한다. AI 배포용 `luvin-staging-github-actions-deploy`(`github_actions.tf`)와는 완전히
분리되어 있고, 서로의 리소스에 접근할 수 없다.

- 신뢰 조건: `token.actions.githubusercontent.com:sub == repo:DASOM-BSSM@269608495/Luvin-Backend-v2@1355673035:ref:refs/heads/master`
  — 이 저장소의 `master` 브랜치에서 실행된 워크플로만 이 role을 assume할 수 있다. 장기 액세스 키는
  쓰지 않는다. `DASOM-BSSM` 조직이 GitHub Actions OIDC subject claim 커스터마이징(조직/저장소 ID
  포함)을 켜 놔서, `sub`가 표준 `repo:OWNER/REPO:ref:...` 형식이 아니라 ID가 붙은 형식으로 나온다
  (실제 워크플로 실행의 토큰으로 확인함).
- 권한은 `luvin-staging-backend` ECR 저장소, `luvin-staging-backend` ECS 서비스, 그 타겟그룹의
  `DescribeTargetHealth`, task definition의 execution/task role에 대한 `iam:PassRole`로만
  한정된다. Secrets Manager 값 자체에 대한 권한은 없다(ECS 실행 role이 시크릿을 읽지, 배포 role이
  읽지 않는다).

## 알아야 할 것 / 이 작업 범위 밖에서 발견한 문제

- **ACM 인증서가 실제 배포 도메인을 커버하지 않는다.** Route53의 `luvin-api.dasom-bssm.com`이
  백엔드 ALB를 가리키는데, ALB HTTPS 리스너에 붙은 인증서(`backend_certificate_arn`)는
  `dasom-bssm.com`/`www.dasom-bssm.com`만 커버한다. 지금 `https://luvin-api.dasom-bssm.com`은
  TLS 인증서 불일치로 실패한다(직접 확인함). `luvin-api.dasom-bssm.com`을 SAN에 포함한 새 ACM
  인증서를 발급하고 리스너를 갱신해야 한다 — 이번 작업 범위 밖이라 고치지 않았다.
- ~~`luvin-ai-v1`의 기존 AI 배포 role의 OIDC 신뢰 조건이 비표준 형식이라 의심했던 부분~~ — 확인
  결과 **AI 쪽이 맞았다.** `DASOM-BSSM` 조직이 OIDC subject claim 커스터마이징을 켜 놔서 조직/저장소
  ID가 `sub`에 포함되는 게 이 조직의 정상 동작이다 (실제 백엔드 워크플로 토큰으로 확인, 위 신뢰
  조건 항목 참고). 처음에 표준 형식으로 만들었다가 실제 배포에서 `AssumeRoleWithWebIdentity` 거부를
  겪고 나서 바로잡았다.

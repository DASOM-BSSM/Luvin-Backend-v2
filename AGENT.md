# AGENTS.md — Luvin Backend

Luvin is a dating-data analytics platform that collects and analyzes users' dating experiences and behavior, visualizes their dating tendencies, and generates an **AI clone** based on user data that participates in dating simulations ("Luvin Hell" / "Solo Hell"). The core idea is not "an AI chatbot" but a pipeline: **data accumulation → tendency analysis → AI clone → simulation → comparison of results**.

This repository is `luvin-backend-v2`, and it is currently **at the MVP development stage**. Several modules were written by different people at different times, so **the package structure and coding conventions are not yet unified.** Do not simply copy conventions from existing code — always check this document, especially §4 (package rules), before writing anything new.

---

> ## Always read before starting any task
>
> 1. Read **§4 Package Rules** first — the repo currently mixes three different root packages, which can break the build
> 2. Read **§8 Module Directory Structure** before creating a new module
> 3. Check **§9 Coding Conventions** before writing a DTO/Service (record vs. class, interface vs. no interface — not yet decided)
> 4. Check **§10 Exception Handling Rules** before throwing an exception — `GlobalExceptionHandler` is currently empty
> 5. Pass **§6 Quality Gate** (verify it compiles) before reporting a task as done

---

# 1. Project Overview

- Service: Luvin — dating-data analysis + AI-clone simulation
- Detailed spec: kept in a separate planning document (recommend splitting into e.g. `PROJECT_SPEC.md`)
- Overall architecture:
  ```
  React (Frontend)
      ↓
  Spring Boot (auth / domain data / DB)
      ↓
  PostgreSQL

  Spring Boot
      ↓
  FastAPI (AI analysis / clone generation / chat / action generation)
      ↓
  AI Model / LLM
  ```
- **AI is a feature of the service, not its purpose.** When designing any new feature, always check: "does this look like it's turning into just an AI chatbot?"

---

# 2. Tech Stack

| Category | Technology | Notes |
|---|---|---|
| Backend (domain) | Spring Boot | Auth, users, surveys, daily questions, emotion diary, shared diary rooms, community, mini-games, simulation data management, DB |
| Backend (AI) | FastAPI | AI analysis, AI clone generation/chat, action generation, NLP, report generation |
| DB | PostgreSQL | (Current code still has `h2-console` permitAll config — confirm whether H2 is still used in parallel during development) |
| Auth | Google OAuth + JWT | No separate signup flow; account is auto-created on first login |
| API docs | Apidog | Must be updated whenever an API changes |
| Dev tools | IntelliJ IDEA, Git, GitHub | |

**On AI**: currently an LLM-API-based MVP. Do not pursue embedding/fine-tuning-level enhancements until enough data has accumulated. Hardcoded response logic currently found in `AnalysisService.chat()`, `SimulationService.finalizeSimulation()`, etc. is **a temporary stand-in before the FastAPI integration**; mark these clearly for later replacement (a `// TODO: replace with FastAPI integration` comment is recommended).

---

# 3. Adding New Dependencies

- Don't add a new library without first checking whether it already exists in `build.gradle` (or `pom.xml`)
- Don't upgrade the Spring Boot / Java version without the user's explicit confirmation
- If a new dependency is needed, share the reason and alternatives first and get confirmation

---

# 4. Package Rules ⚠️ Top Priority

**Three different root-package lineages currently coexist in this repository.**

| Lineage | Example | Characteristics |
|---|---|---|
| A | `test.luvin_backend_v2.*` (declaration) + `com.luvin_backend_v2.*` (imports) | `LuvinBackendV2Application` (`@SpringBootApplication`), plus `analysis`, `auth`, `common`, `dailyquestion`, and others. **The declared package and the imported package point to different roots, so this fails to compile as-is** |
| B | Consistently `com.luvin.*` | `simulation`, `survey`, `user` modules. Internally consistent, but separate from the tree scanned by `@SpringBootApplication` (`test.luvin_backend_v2`), so Spring cannot find these beans |
| C | Empty placeholders (`diary`, `diaryroom`, `minigame`, `global`) | Not yet implemented. Which lineage they'll follow is still undecided |

**Decisions needed (update this section once the team confirms):**
- [ ] What is the final root package name? (Recommend standardizing on `test.luvin_backend_v2`, where `@SpringBootApplication` currently lives)
- [ ] Should the shared/common module package be named `common` or `global`?
- [ ] Who/when will do the bulk unification pass (a single sweeping rename is recommended over fixing files one by one)?

**Interim rules until this is resolved:**
- Place any new file under the package that contains `@SpringBootApplication` (`test.luvin_backend_v2`), so it's at least picked up by component scanning
- If you need to touch a lineage-B file (`com.luvin.*`), don't just quietly patch it — flag the package issue to the team first
- Before writing a new import, verify where that class is actually declared — don't copy import statements from existing code (lineage-A files' imports don't match their actual declarations)

---

# 5. Environment Variables / Configuration

- Spring Boot: `application.yml` (check values bound via `@ConfigurationProperties`, e.g. `app.jwt.secret`, `app.jwt.access-token-expiration-seconds`)
- Secrets (JWT secret, Google OAuth client secret, DB password, etc.) must not be committed directly in `application.yml` — use environment variables or a separate gitignored `application-local.yml`
- How the FastAPI side manages its `.env` needs its own section (add once the FastAPI repo structure is reviewed)

---

# 6. Code Quality Gate

**Current state: no separate lint/format tooling or CI yet.**

## What every task must do right now
- Before reporting a change as complete, **actually verify it compiles** (re-check import paths every time, especially given the §4 package issue)
- Verify that any newly referenced field/method actually exists (e.g., the case where `UserServiceImpl.getProfile()` referenced a `surveyAnswerRepository` that was never injected)

## Target setup (proceed once the team approves)
- Unify formatting with Checkstyle or Spotless
- GitHub Actions CI: run `./gradlew build` (compile + test) on every PR
- A process to check that the Apidog docs match the actual API spec

---

# 7. Testing Strategy

**Current state: no test code yet.**

- Even at the MVP stage, keep core business logic (dating-tendency scoring, the "one-click mode" option logic, simulation round progression, etc.) as pure Service-layer methods so tests can be added easily later
- At minimum, consider testing these first:
    - The scoring/type-determination logic in `AnalysisService`
    - The answer-validation logic in `SurveyServiceImpl` / `DailyQuestionServiceImpl` (duplicate-answer prevention, option-ownership checks)
    - JWT issuance/validation (`JwtTokenProvider`)

---

# 8. Module Directory Structure

Each domain module should default to the following sub-structure (some modules are currently flat and need to be unified):

```
<module>/
├── controller/     # @RestController, mapped under /api/<module>
├── service/        # interface + Impl, OR a single Service class (see §9 — needs unification)
├── repository/     # JpaRepository
├── domain/         # @Entity
└── dto/            # Request/Response
```

Current implementation status per module:

| Module | Status | Notes |
|---|---|---|
| `auth` | Implemented | Google OAuth verification is mocked (only checks whether `googleToken.startsWith("invalid")`) — real Google token verification needs to be added |
| `user` | Implemented | `getProfile()` has a compile error (needs fixing independently of the §4 decision) |
| `analysis` | Implemented | Scoring is rule-based (MBTI / answer count); survey `effects` values are not yet factored in |
| `dailyquestion` | Implemented | |
| `survey` | Implemented | `/{surveyId}/option` only returns the first question — confirm whether a per-question option lookup API is needed |
| `simulation` | Partially implemented | Clone creation / simulation creation / status / actions / interventions / report / matching / final couple are implemented. Participants, episodes, chat, voting, reroll, branching, AI-decision-rationale, feedback, and highlights are not yet implemented |
| `diary` | Not implemented (empty scaffold) | |
| `diaryroom` | Not implemented (empty scaffold) | |
| `minigame` | Not implemented (empty scaffold) | |
| `common` / `global` | Partially implemented | `common` has real implementations; `global` is an empty scaffold — naming needs to be unified |

---

# 9. Coding Conventions (currently mixed — needs unification)

Style currently differs by module. New code should follow whichever of the two below the team decides on; until then, default to **the style of the most recently written module**.

| Item | Style A (`analysis`, `auth`) | Style B (`dailyquestion`, `survey`) |
|---|---|---|
| DTOs | Java `record` | Traditional classes (getters/setters) |
| Service | Single class, no interface | Interface + `*Impl` implementation |
| Entity constructor | Lombok `@Builder` | Explicit public constructor |
| User reference | `@ManyToOne User user` (direct FK reference) | `Long memberId` (ID only) |

**Decisions needed:**
- [ ] Should DTOs be standardized on `record`?
- [ ] Should Services be standardized on interface+Impl, or a single class?
- [ ] Should the User relationship be standardized on an FK reference, or storing just the ID?

---

# 10. Exception Handling Rules

- Shared infrastructure already exists: `ErrorCode` (enum) + `BusinessException` + `ApiResponse.error()`
- **`GlobalExceptionHandler` is currently completely empty.** It needs `@ExceptionHandler(BusinessException.class)` and similar handlers added so responses come back as `ApiResponse.error(...)` with the matching `ErrorCode`'s HttpStatus — without this, every exception falls through to Spring's default 500 response
- The current domain-specific exceptions (`SurveyNotFoundException`, `DailyQuestionNotFoundException`, etc.) extend `RuntimeException` directly rather than `BusinessException`. When filling in `GlobalExceptionHandler`, decide whether to also handle these, or to standardize everything on the `BusinessException` + `ErrorCode` pattern
- When adding a new exception, don't invent a new style on your own — follow the existing domain-exception pattern until this decision is made

---

# 11. API Response Rules

- Standard wrapper: `ApiResponse<T>` (`ok`, `okMessage`, `error`)
- Some controllers (`dailyquestion`, `survey`) don't use `ApiResponse` and instead return `MessageResponse` or a DTO directly — needs unification
- Default to wrapping new APIs in `ApiResponse<T>` (to keep them aligned with the spec / Apidog docs)
- REST conventions: GET (read) / POST (create · submit · join) / PUT (full update) / PATCH (partial update) / DELETE (delete)
- Update the Apidog docs in the same PR whenever an API changes

---

# 12. Authentication / Security

- JWT-based stateless auth, via the `Authorization: Bearer <token>` header
- `JwtAuthenticationFilter` sets an `AuthenticatedUser` in `SecurityContextHolder`; look it up afterward with `SecurityUtils.getCurrentUserId()`
- Logout works by registering the token in a blacklist (`TokenBlacklistService`)
- There is currently no real Google OAuth token verification (it's a mock that only checks for an `"invalid"` prefix) — **real Google Identity verification must be added before launch**
- New users are created with hardcoded default values for age/mbti/datingStyle (`20`, `"INFP"`, `"신중형"`) — confirm whether the onboarding flow later updates these to real values
- Only `/health`, `/api/auth/**`, and `/h2-console/**` are accessible without authentication; everything else requires it

---

# 13. AI Clone / Simulation Data Flow

- `AnalysisResult` (dating-tendency analysis) → referenced when creating an `AiClone` → used within a `Simulation`
- `analysis.getClone()` and `simulation.createClone()` currently **duplicate the same clone-creation logic** — consider extracting it into shared logic
- The `effects` field on survey/daily-question options (formatted like `"관계불안도:+20"`) is stored but not yet parsed into `AnalysisService`'s scoring — actually wiring this up looks like the next priority for improving the analysis
- User intervention in a simulation (`intervene`) currently only saves the message and changes status; it does not yet feed into subsequent action generation (`generateAction`) — this needs to be wired up to satisfy the "intervention → changed outcome" requirement from the product spec (§7, user intervention)

---

# 14. Patterns to Avoid (based on issues actually found)

- Don't copy import statements from another module — verify where the class actually lives (§4)
- Don't reference a field/repository in a service that was never injected — always check the constructor's injection list
- Don't decide ad hoc whether a new domain exception should extend `BusinessException` or `RuntimeException` (§10)
- Don't decide ad hoc whether an API response should be wrapped in `ApiResponse` (§11)
- Don't copy-paste the same logic (e.g., AI-clone creation rules) into another module — check first whether it should be shared
- Don't leave fields like `effects` "stored but unused" in seed data without a plan — if there's no plan to use it, question why it's being stored
- When creating new empty placeholder files (files literally named `dto`, `controller`, `service`, `repository`, `entity`, matching their role name), rename them to the actual domain class name when implementing (e.g. `DiaryController`, `DiaryEntry`) — the current `diary`/`diaryroom`/`minigame` placeholders are named after their role and will need renaming on implementation

---

# 15. Git Conventions

(Confirm whether these match the frontend's `CONTRIBUTING.md` — fill this section in once confirmed)

- Whether branch/commit conventions are unified with the frontend
- Compilation must be verified before opening a PR
- No direct commits to `main` (assumed — needs confirmation)

---

# 16. Currently Known Tech Debt (unordered list, to be prioritized later)

1. Three coexisting package lineages → currently fails to compile (§4)
2. Compile error in `UserServiceImpl.getProfile()` (`surveyAnswerRepository` never injected)
3. `GlobalExceptionHandler` is empty
4. No real Google OAuth token verification (currently mocked)
5. Duplicated AI-clone creation logic between `analysis` and `simulation`
6. Survey / daily-question `effects` field not yet factored into scoring
7. The survey-option lookup API appears to only return the first question (intent needs confirmation)
8. `diary`, `diaryroom`, `minigame` modules entirely unimplemented
9. Simulation participants / episodes / chat / voting / reroll / branching / AI-decision-rationale / feedback / highlights APIs unimplemented
10. Relationship between `User.personalityType` ("bread type") and `AnalysisResult.datingStyle` — two classification systems not yet reconciled
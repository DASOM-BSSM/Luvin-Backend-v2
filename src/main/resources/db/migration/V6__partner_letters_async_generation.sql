-- "SECRET LOVE" 쪽지를 Spring이 report narrative로 즉시 조립하던 방식에서, AI 서비스가
-- POST/GET /v1/seasons/{season_id}/letter로 비동기 생성하는 job 방식으로 바꾼다.
--
-- partner_letters는 기존에 ai_episode_progresses/ai_reroll_requests와 같은 Hibernate
-- ddl-auto:update 관리 테이블이다(Flyway 관리 대상이 아니었음). 이번에 그 두 엔티티와 동일한
-- job 추적 컬럼(job_id/job_status/error_code/next_poll_at/poll_attempts/jpa_version)을
-- 추가하면서, job_status는 @Enumerated(STRING)이라 Hibernate가 CHECK 제약을 자동 생성하는
-- 컬럼인데 — 이 프로젝트에서 반복 확인된 대로 ddl-auto는 "자신이 처음 만드는 컬럼"에만 CHECK를
-- 붙이므로, Flyway가 먼저 컬럼을 만들어버리면 CHECK가 영영 생기지 않는다. 그래서 CHECK까지
-- 여기서 직접 만든다.
--
-- partner_id/message는 기존에 NOT NULL이었다(즉시 조립 시점에 둘 다 항상 있었으므로). 이제는
-- job이 성공(SUCCEEDED)하기 전까지는 둘 다 모를 수 있어 nullable로 완화한다.
--
-- 빈 DB(CI)는 테이블이 아직 없어 조건부로 no-op 처리되고, 이후 Hibernate가 새 엔티티 정의
-- 그대로 테이블을 새로 만든다(이 경우 job_status CHECK도 Hibernate가 정상적으로 자동 생성한다).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'partner_letters') THEN
        ALTER TABLE partner_letters ALTER COLUMN partner_id DROP NOT NULL;
        ALTER TABLE partner_letters ALTER COLUMN message DROP NOT NULL;

        ALTER TABLE partner_letters ADD COLUMN IF NOT EXISTS job_id uuid;
        ALTER TABLE partner_letters ADD COLUMN IF NOT EXISTS job_status VARCHAR(20);
        ALTER TABLE partner_letters ADD COLUMN IF NOT EXISTS error_code VARCHAR(100);
        ALTER TABLE partner_letters ADD COLUMN IF NOT EXISTS render_mode VARCHAR(50);
        ALTER TABLE partner_letters ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0;
        ALTER TABLE partner_letters ADD COLUMN IF NOT EXISTS requested_at TIMESTAMP;
        ALTER TABLE partner_letters ADD COLUMN IF NOT EXISTS next_poll_at TIMESTAMP;
        ALTER TABLE partner_letters ADD COLUMN IF NOT EXISTS poll_attempts INT NOT NULL DEFAULT 0;
        ALTER TABLE partner_letters ADD COLUMN IF NOT EXISTS jpa_version BIGINT NOT NULL DEFAULT 0;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            WHERE rel.relname = 'partner_letters' AND con.conname = 'partner_letters_job_status_check'
        ) THEN
            ALTER TABLE partner_letters ADD CONSTRAINT partner_letters_job_status_check
                CHECK (job_status IN ('QUEUED', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED'));
        END IF;
    END IF;
END $$;

-- AiIdempotencyActionType에 PARTNER_LETTER_GENERATION을 추가하면서 똑같은 함정을 반복하지
-- 않기 위해, V5 핫픽스와 동일하게 ai_idempotency_keys.action_type CHECK 제약을 여기서 함께
-- 최신 6개 값으로 갱신한다(ddl-auto는 기존 제약을 절대 고치지 않는다).
DO $$
DECLARE
    old_constraint_name text;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_idempotency_keys') THEN
        SELECT con.conname INTO old_constraint_name
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'ai_idempotency_keys'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%action_type%';

        IF old_constraint_name IS NOT NULL THEN
            EXECUTE format('ALTER TABLE ai_idempotency_keys DROP CONSTRAINT %I', old_constraint_name);
        END IF;

        ALTER TABLE ai_idempotency_keys ADD CONSTRAINT ai_idempotency_keys_action_type_check
            CHECK (action_type IN ('SEASON_CREATE', 'SEASON_CREATE_NEXT', 'EPISODE_GENERATION',
                                    'EPISODE_SELECTION', 'EPISODE_REROLL', 'PARTNER_LETTER_GENERATION'));
    END IF;
END $$;

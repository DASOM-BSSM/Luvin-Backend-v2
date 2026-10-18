-- 핫픽스: POST /api/ai/seasons/next 전체 장애 수정.
--
-- Hibernate(ORM 7)는 @Enumerated(EnumType.STRING) 컬럼에 대해 "그 시점의" enum 값 목록으로
-- CHECK 제약을 자동 생성한다(이 프로젝트의 ai_seasons.status, ai_episode_progresses.job_status
-- 등에서도 동일하게 관찰됨). ai_idempotency_keys.action_type의 CHECK 제약은 AiIdempotencyActionType에
-- SEASON_CREATE_NEXT를 추가하기 "전" 4개 값(SEASON_CREATE, EPISODE_GENERATION,
-- EPISODE_SELECTION, EPISODE_REROLL)으로 이미 운영 DB에 만들어져 있었다.
--
-- ddl-auto:update는 기존 제약을 절대 고치지 않으므로(지금까지 이 저장소의 여러 마이그레이션에서
-- 반복 확인한 사실), SEASON_CREATE_NEXT로 INSERT하는 모든 요청이 DataIntegrityViolationException
-- -> 500으로 실패하고 있었다(/next를 쓰는 모든 유저에게 영향).
--
-- 빈 DB(CI)는 테이블이 아직 없어 조건부로 no-op 처리되고, 이후 Hibernate가 현재 enum 값
-- 그대로(5개) 테이블을 새로 만든다. 이미 구 제약이 있는 환경(스테이징)에서만 아래가 실제로
-- 동작해 제약을 최신 5개 값으로 교체한다.
--
-- 제약을 직접 재생성한다(ddl-auto에 맡기지 않는다) — V4(minigames.code UNIQUE)에서 ddl-auto가
-- 새 제약을 안정적으로 추가해주지 않는 걸 이미 확인했고, 같은 불확실성을 CHECK 제약에도
-- 적용하지 않기 위해서다.
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
                                    'EPISODE_SELECTION', 'EPISODE_REROLL'));
    END IF;
END $$;

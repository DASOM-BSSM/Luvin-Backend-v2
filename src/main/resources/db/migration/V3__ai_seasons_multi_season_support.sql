-- 러빈지옥이 "다음 시즌"을 지원하도록 ai_seasons/ai_season_creation_inputs를 member_id 당 1행에서
-- member_id + season_number(1부터) 당 1행으로 바꾼다. 둘 다 Hibernate ddl-auto:update가 관리하는
-- 테이블이라(Flyway 관리 대상이 아님) 이 마이그레이션은 "ddl-auto가 할 수 없는 구조 변경"만 한다:
--   - 기존 member_id 단독 UNIQUE 제약 제거(ddl-auto는 더 이상 선언되지 않은 제약을 절대 안 지운다)
--   - season_number 컬럼 추가 + 기존 행 전부 1로 백필(기존에는 회원당 시즌이 정확히 1개뿐이었으므로
--     그게 항상 1번째 시즌이다) + NOT NULL
--   - (member_id, season_number) 새 UNIQUE 제약 추가(동시 생성 경쟁의 최종 안전장치)
--
-- Flyway는 항상 Hibernate ddl-auto보다 먼저 실행된다. CI처럼 완전히 빈 DB에서는 이 두 테이블이
-- 아직 존재하지 않으므로 전부 IF EXISTS/조건부로 no-op 처리되고, 이후 Hibernate가 엔티티 정의
-- (season_number 포함) 그대로 테이블을 새로 만든다. 운영(스테이징)처럼 이미 테이블과 데이터가 있는
-- 경우에만 아래 DO 블록이 실제로 동작한다.
--
-- UNIQUE 제약 이름은 Hibernate가 자동 생성한 해시 기반 이름이라 미리 알 수 없다(예:
-- ai_episode_progresses에서 실제로 "ukowum7u207bwnhxtvwpipxnyx3" 같은 이름으로 확인됨). 그래서
-- 이름을 하드코딩하지 않고 pg_constraint에서 "member_id 단일 컬럼으로 이루어진 UNIQUE 제약"을
-- 직접 찾아서 지운다.
DO $$
DECLARE
    old_constraint_name text;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_seasons') THEN
        SELECT con.conname INTO old_constraint_name
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'ai_seasons'
          AND con.contype = 'u'
          AND con.conkey = ARRAY[(
              SELECT attnum FROM pg_attribute
              WHERE attrelid = rel.oid AND attname = 'member_id'
          )];

        IF old_constraint_name IS NOT NULL THEN
            EXECUTE format('ALTER TABLE ai_seasons DROP CONSTRAINT %I', old_constraint_name);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'ai_seasons' AND column_name = 'season_number'
        ) THEN
            ALTER TABLE ai_seasons ADD COLUMN season_number INT NOT NULL DEFAULT 1;
            ALTER TABLE ai_seasons ALTER COLUMN season_number DROP DEFAULT;
        END IF;

        -- 새 (member_id, season_number) UNIQUE는 여기서 만들지 않는다 — Hibernate ddl-auto가
        -- 엔티티의 @Table(uniqueConstraints=...)를 보고 다음 기동 때 알아서 추가한다(없는 제약은
        -- 추가하는 건 ddl-auto:update가 할 수 있는 일이다). 여기서 직접 만들면 이름만 다른
        -- 중복 UNIQUE 제약이 두 개 생긴다(실제로 재현해서 확인함).
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_season_creation_inputs') THEN
        SELECT con.conname INTO old_constraint_name
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'ai_season_creation_inputs'
          AND con.contype = 'u'
          AND con.conkey = ARRAY[(
              SELECT attnum FROM pg_attribute
              WHERE attrelid = rel.oid AND attname = 'member_id'
          )];

        IF old_constraint_name IS NOT NULL THEN
            EXECUTE format('ALTER TABLE ai_season_creation_inputs DROP CONSTRAINT %I', old_constraint_name);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'ai_season_creation_inputs' AND column_name = 'season_number'
        ) THEN
            ALTER TABLE ai_season_creation_inputs ADD COLUMN season_number INT NOT NULL DEFAULT 1;
            ALTER TABLE ai_season_creation_inputs ALTER COLUMN season_number DROP DEFAULT;
        END IF;

        -- 위 ai_seasons와 같은 이유로 새 UNIQUE는 여기서 만들지 않는다(ddl-auto가 추가한다).
    END IF;
END $$;

-- ai_seasons.completed_at: complete() 호출 시각. 기존 Hibernate 관리 컬럼이라 nullable로 추가만
-- 하면 되고 백필이 필요 없다(과거에 완료된 시즌의 정확한 완료 시각은 복원할 수 없어 null로 둔다 —
-- GET /api/ai/seasons 목록에서 이 값이 비어 있는 과거 행은 "완료 시각 기록 전" 데이터로 취급한다).
ALTER TABLE IF EXISTS ai_seasons ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;

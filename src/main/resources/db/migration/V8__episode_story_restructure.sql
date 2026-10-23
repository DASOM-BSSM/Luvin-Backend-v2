-- 러빈지옥 에피소드 재구성(장면 블록 1~10화).
--
-- 새 테이블(ai_season_choices, ai_bread_relations, ai_episode_contents, ai_season_story_reports)은
-- 다른 AI 테이블과 같이 Hibernate ddl-auto:update가 만든다 — Flyway는 Hibernate보다 먼저 돌아서,
-- 빈 DB(CI)에서는 FK로 참조할 ai_seasons가 아직 없기 때문이다.
--
-- 여기서는 이미 있는 테이블만 바꾼다. 빈 DB는 테이블이 없어 조건부로 no-op이고, 이후 Hibernate가
-- 새 엔티티 정의 그대로(CHECK 제약 포함) 만든다. 기존 테이블에 enum 컬럼을 더할 때는 V6처럼
-- CHECK까지 직접 만든다(ddl-auto는 기존 테이블의 제약을 안정적으로 추가·수정하지 않는다).

-- 1. users.age: 가입 시 넣던 20은 사용자가 입력한 값이 아니다(나이를 바꾸는 API도 없었다).
--    입력 전으로 보고 null 처리한다 — 1화 진입 시 나이를 입력받는다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'users') THEN
        UPDATE users SET age = NULL WHERE age = 20;
    END IF;
END $$;

-- 2. ai_seasons: 시즌 배경, 새 흐름 진행 회차, 6화 이후 흐름(route)과 연애 상대.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_seasons') THEN
        ALTER TABLE ai_seasons ADD COLUMN IF NOT EXISTS setting VARCHAR(20);
        ALTER TABLE ai_seasons ADD COLUMN IF NOT EXISTS current_order INT;
        ALTER TABLE ai_seasons ADD COLUMN IF NOT EXISTS route VARCHAR(20);
        ALTER TABLE ai_seasons ADD COLUMN IF NOT EXISTS romance_partner_id uuid;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ai_seasons_setting_check') THEN
            ALTER TABLE ai_seasons ADD CONSTRAINT ai_seasons_setting_check
                CHECK (setting IN ('SCHOOL', 'CAMPUS', 'CAFE', 'OFFICE'));
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ai_seasons_route_check') THEN
            ALTER TABLE ai_seasons ADD CONSTRAINT ai_seasons_route_check
                CHECK (route IN ('ROMANCE', 'SOLO'));
        END IF;
    END IF;
END $$;

-- 3. ai_season_characters: 출연진 역할·애착 성향·이름, 그리고 Spring이 추가하는 출연진(SUPPORTING) role.
DO $$
DECLARE
    old_constraint_name text;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_season_characters') THEN
        ALTER TABLE ai_season_characters ADD COLUMN IF NOT EXISTS cast_role VARCHAR(20);
        ALTER TABLE ai_season_characters ADD COLUMN IF NOT EXISTS attachment_style VARCHAR(20);
        ALTER TABLE ai_season_characters ADD COLUMN IF NOT EXISTS name VARCHAR(50);

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ai_season_characters_cast_role_check') THEN
            ALTER TABLE ai_season_characters ADD CONSTRAINT ai_season_characters_cast_role_check
                CHECK (cast_role IN ('MY_BREAD', 'AVOIDANT', 'ANXIOUS', 'SECURE', 'FRIEND', 'MOOD_MAKER'));
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ai_season_characters_attachment_style_check') THEN
            ALTER TABLE ai_season_characters ADD CONSTRAINT ai_season_characters_attachment_style_check
                CHECK (attachment_style IN ('AVOIDANT', 'ANXIOUS', 'SECURE', 'NONE'));
        END IF;

        SELECT con.conname INTO old_constraint_name
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'ai_season_characters'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%(role)%';

        IF old_constraint_name IS NOT NULL THEN
            EXECUTE format('ALTER TABLE ai_season_characters DROP CONSTRAINT %I', old_constraint_name);
        END IF;

        ALTER TABLE ai_season_characters ADD CONSTRAINT ai_season_characters_role_check
            CHECK (role IN ('REPRESENTATIVE', 'CANDIDATE', 'SUPPORTING'));
    END IF;
END $$;

-- 4. partner_letters: 회차 완료 쪽지가 회차마다 생기므로 season_id unique를 (season_id, episode_order)로 바꾼다.
--    기존 5화 흐름 쪽지(episode_order null)는 여전히 시즌당 1개만 허용한다(partial unique index).
DO $$
DECLARE
    old_constraint_name text;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'partner_letters') THEN
        ALTER TABLE partner_letters ADD COLUMN IF NOT EXISTS episode_order INT;

        FOR old_constraint_name IN
            SELECT con.conname
            FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            WHERE rel.relname = 'partner_letters'
              AND con.contype = 'u'
              AND pg_get_constraintdef(con.oid) = 'UNIQUE (season_id)'
        LOOP
            EXECUTE format('ALTER TABLE partner_letters DROP CONSTRAINT %I', old_constraint_name);
        END LOOP;

        -- (season_id, episode_order) unique는 엔티티 정의대로 Hibernate가 추가한다.
        CREATE UNIQUE INDEX IF NOT EXISTS partner_letters_season_legacy_key
            ON partner_letters (season_id) WHERE episode_order IS NULL;
    END IF;
END $$;

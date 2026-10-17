-- minigames에 title과 달리 바뀌지 않는 식별 키 code를 추가한다. title 문자열 비교로 게임을
-- 찾던 프론트가 title이 조금만 바뀌어도 보상이 전혀 안 나가는 문제가 있어 추가한다.
--
-- minigames는 Flyway가 아니라 Hibernate ddl-auto가 만드는 테이블이라(SeedDataLoader로 시드),
-- Flyway는 항상 그보다 먼저 실행된다. 빈 DB(CI)에서는 아직 테이블이 없으니 전부 조건부로
-- no-op 처리되고, 이후 Hibernate가 엔티티 정의(code NOT NULL UNIQUE 포함) 그대로 테이블을
-- 새로 만든다. 이미 시드돼 있는 환경(스테이징)에서만 아래가 실제로 동작해 기존 두 행을
-- title 기준으로 백필한다 — 이 두 title/code 매핑은 SeedDataLoader.seedMinigames()와
-- 반드시 같게 유지해야 한다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'minigames') THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'minigames' AND column_name = 'code'
        ) THEN
            ALTER TABLE minigames ADD COLUMN code VARCHAR(50);

            UPDATE minigames SET code = 'dinosaur_bread_game' WHERE title = '공룡빵게임';
            UPDATE minigames SET code = 'bread_crossing_friends' WHERE title = '빵건너친구들';

            ALTER TABLE minigames ALTER COLUMN code SET NOT NULL;
        END IF;

        -- V3와 달리 여기서는 ddl-auto가 새 UNIQUE 제약을 안정적으로 추가해주지 않는 것을
        -- 실제로 확인해서(재부팅해도 안 생김), 직접 추가한다. IF NOT EXISTS로 재실행/ddl-auto의
        -- 중복 시도 양쪽 다 안전하게 막는다.
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            WHERE rel.relname = 'minigames' AND con.contype = 'u'
              AND con.conkey = ARRAY[(
                  SELECT attnum FROM pg_attribute WHERE attrelid = rel.oid AND attname = 'code'
              )]
        ) THEN
            ALTER TABLE minigames ADD CONSTRAINT uk_minigames_code UNIQUE (code);
        END IF;
    END IF;
END $$;

-- diary_room에 영구 초대 코드 invite_code(8자리)를 추가한다. 코드를 아는 사람은 누구나 방에 참여할 수 있다.
--
-- diary_room은 Flyway가 아니라 Hibernate ddl-auto가 만드는 테이블이라, Flyway는 항상 그보다 먼저 실행된다.
-- 빈 DB(CI)에서는 아직 테이블이 없으니 전부 조건부로 no-op 처리되고, 이후 Hibernate가 엔티티 정의
-- (invite_code NOT NULL UNIQUE 포함) 그대로 테이블을 새로 만든다.
-- 이미 방이 있는 환경(스테이징)에서만 아래가 실제로 동작한다:
--   1) 컬럼 추가 → 2) 기존 방마다 겹치지 않는 코드 백필 → 3) NOT NULL → 4) UNIQUE 제약
--
-- 백필을 여기서 하는 이유: 앱에서 "처음 조회할 때 발급"하면 방장·멤버가 동시에 조회할 때 서로 다른 코드가
-- 만들어지고 하나만 남아, 먼저 받은 사람이 공유한 코드가 404가 된다.
-- UNIQUE를 여기서 직접 만드는 이유: V4와 같이, 이미 있는 테이블에 추가되는 컬럼의 UNIQUE는 ddl-auto가
-- 안정적으로 만들어주지 않는다(CI는 빈 DB라 제약이 생겨 테스트로는 안 잡힌다).
--
-- 코드 글자/길이는 DiaryRoomService의 INVITE_CODE_CHARS / INVITE_CODE_LENGTH와 반드시 같게 유지해야 한다.
DO $$
DECLARE
    chars CONSTANT TEXT := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';  -- 헷갈리는 0/O, 1/I/L 제외
    room RECORD;
    code TEXT;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'diary_room') THEN
        ALTER TABLE diary_room ADD COLUMN IF NOT EXISTS invite_code VARCHAR(8);

        FOR room IN SELECT id FROM diary_room WHERE invite_code IS NULL LOOP
            LOOP
                SELECT string_agg(substr(chars, 1 + floor(random() * length(chars))::int, 1), '')
                INTO code
                FROM generate_series(1, 8);
                EXIT WHEN NOT EXISTS (SELECT 1 FROM diary_room WHERE invite_code = code);
            END LOOP;
            UPDATE diary_room SET invite_code = code WHERE id = room.id;
        END LOOP;

        ALTER TABLE diary_room ALTER COLUMN invite_code SET NOT NULL;

        -- 재실행/ddl-auto의 중복 시도 양쪽 다 안전하게 막는다 (컬럼에 걸린 UNIQUE가 이미 있으면 건너뜀).
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            WHERE rel.relname = 'diary_room' AND con.contype = 'u'
              AND con.conkey = ARRAY[(
                  SELECT attnum FROM pg_attribute WHERE attrelid = rel.oid AND attname = 'invite_code'
              )]
        ) THEN
            ALTER TABLE diary_room ADD CONSTRAINT uk_diary_room_invite_code UNIQUE (invite_code);
        END IF;
    END IF;
END $$;

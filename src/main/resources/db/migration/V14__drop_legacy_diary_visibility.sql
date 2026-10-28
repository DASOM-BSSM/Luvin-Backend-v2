-- 일기 공개범위(visibility)는 10-04(c69c4b4)에 엔티티에서 지웠지만, ddl-auto: update는 컬럼을 지우지 않는다.
-- 그 전에 만들어진 스테이징 diary 테이블에는 visibility NOT NULL(+ PRIVATE/ROOM/PUBLIC CHECK)이 남아 있어서,
-- 엔티티가 값을 넣지 않는 INSERT가 매번 NOT NULL 위반(500)으로 실패했다. 컬럼을 지우면 CHECK도 같이 지워진다.
--
-- 같은 때 일기가 방에 필수가 되었는데(room_id nullable = false) 기존 테이블의 room_id는 NULL 허용으로 남아 있다.
-- 방 없는 일기가 없을 때만 NOT NULL을 건다(있으면 그대로 두고 따로 정리한다).
--
-- diary는 Hibernate ddl-auto가 만드는 테이블이라 Flyway가 먼저 실행된다. 빈 DB(CI)에서는 테이블이 없으니 no-op이다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'diary') THEN
        ALTER TABLE diary DROP COLUMN IF EXISTS visibility;

        IF NOT EXISTS (SELECT 1 FROM diary WHERE room_id IS NULL) THEN
            ALTER TABLE diary ALTER COLUMN room_id SET NOT NULL;
        END IF;
    END IF;
END $$;

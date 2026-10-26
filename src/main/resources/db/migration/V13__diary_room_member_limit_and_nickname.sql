-- 공유방 인원 제한(diary_room.max_member_count)과 방별 닉네임(diary_room_member.nickname)을 추가한다.
--
-- V7과 같이 diary_room / diary_room_member는 Hibernate ddl-auto가 만드는 테이블이라 Flyway가 먼저 실행된다.
-- 빈 DB(CI)에서는 테이블이 없으니 no-op이고, 이후 Hibernate가 엔티티 정의(NOT NULL, UNIQUE 포함) 그대로 만든다.
-- 이미 방이 있는 환경에서만 아래가 동작한다: 컬럼 추가 → 기존 행 백필 → NOT NULL → UNIQUE.
-- (ddl-auto는 데이터가 있는 테이블에 NOT NULL 컬럼을 추가하지 못하므로 여기서 먼저 만든다.)
--
-- 기존 방은 새 방의 최대값(6명)으로 채운다. 이미 6명이 넘는 방도 지금 멤버는 그대로 두고, 새 참여만 막힌다.
-- 기존 멤버의 닉네임은 계정 닉네임(없으면 이름)을 앞 10자까지 쓴다(DiaryRoomDto.defaultNickname과 같은 규칙).
-- 같은 방에서 닉네임이 겹치면 방장 → 먼저 들어온 사람 순으로 원래 닉네임을 두고, 나머지는 뒤에 숫자를 붙인다
-- (예: 멤버 → 멤버2). 그 뒤 UNIQUE(room_id, nickname)를 건다.
DO $$
DECLARE
    dup RECORD;
    n INT;
    candidate TEXT;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'diary_room') THEN
        ALTER TABLE diary_room ADD COLUMN IF NOT EXISTS max_member_count INTEGER;
        UPDATE diary_room SET max_member_count = 6 WHERE max_member_count IS NULL;
        ALTER TABLE diary_room ALTER COLUMN max_member_count SET NOT NULL;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'diary_room_member') THEN
        ALTER TABLE diary_room_member ADD COLUMN IF NOT EXISTS nickname TEXT;
        UPDATE diary_room_member m
        SET nickname = left(COALESCE(NULLIF(btrim(u.nickname), ''), NULLIF(btrim(u.name), ''), '멤버'), 10)
        FROM users u
        WHERE u.user_id = m.user_id AND m.nickname IS NULL;
        ALTER TABLE diary_room_member ALTER COLUMN nickname SET NOT NULL;

        FOR dup IN
            SELECT room_id, user_id, nickname FROM (
                SELECT room_id, user_id, nickname,
                       row_number() OVER (PARTITION BY room_id, nickname
                                          ORDER BY (role = 'OWNER') DESC, joined_at, user_id) AS rn
                FROM diary_room_member
            ) ranked
            WHERE rn > 1
        LOOP
            n := 2;
            LOOP
                candidate := left(dup.nickname, 10 - length(n::text)) || n;
                EXIT WHEN NOT EXISTS (
                    SELECT 1 FROM diary_room_member WHERE room_id = dup.room_id AND nickname = candidate
                );
                n := n + 1;
            END LOOP;
            UPDATE diary_room_member SET nickname = candidate
            WHERE room_id = dup.room_id AND user_id = dup.user_id;
        END LOOP;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_diary_room_member_room_nickname') THEN
            ALTER TABLE diary_room_member
                ADD CONSTRAINT uk_diary_room_member_room_nickname UNIQUE (room_id, nickname);
        END IF;
    END IF;
END $$;

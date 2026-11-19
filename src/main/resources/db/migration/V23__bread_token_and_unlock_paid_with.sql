-- 토큰 두 종류(LUV-116): 소금빵(무료, 기존 token_balance)과 식빵(유료, bread_token_balance).
-- user_tokens·ai_episode_unlocks는 Hibernate ddl-auto가 관리하지만, NOT NULL 컬럼은 기존 행이 있으면 Hibernate가 더하지 못하므로
-- 여기서 기본값 0으로 더한다. 회차를 연 토큰 종류(paid_with)는 이전 기록이 소금빵이라 null로 둔다(null = SALT).
-- 회차 생성 완료 푸시 중복 방지 컬럼(ai_episode_contents.ready_notified_at)도 더한다. 기존 READY 회차는 이미 끝난 생성이라
-- 알림 대상이 아니므로 null로 둔다(알림은 생성이 끝나는 순간에만 보낸다).
-- Flyway가 Hibernate보다 먼저 돌므로 빈 DB(CI)에서는 테이블이 없어 no-op이고, 이후 Hibernate가 컬럼까지 만든다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'user_tokens') THEN
        ALTER TABLE user_tokens ADD COLUMN IF NOT EXISTS bread_token_balance INTEGER NOT NULL DEFAULT 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_episode_unlocks') THEN
        ALTER TABLE ai_episode_unlocks ADD COLUMN IF NOT EXISTS paid_with VARCHAR(10);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_episode_contents') THEN
        ALTER TABLE ai_episode_contents ADD COLUMN IF NOT EXISTS ready_notified_at TIMESTAMP(6);
    END IF;
END $$;

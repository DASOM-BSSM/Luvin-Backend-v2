-- 러빈지옥 회차 생성 완료 푸시(EPISODE_READY)를 보내지 않는다(LUV-127, 앱이 회차 공개 시각에 기기 알림을 직접 건다).
-- 중복 전송을 막던 기록 컬럼(V23)을 지운다.
-- Flyway가 Hibernate보다 먼저 돌므로 빈 DB(CI)에서는 테이블이 없어 no-op이다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_episode_contents') THEN
        ALTER TABLE ai_episode_contents DROP COLUMN IF EXISTS ready_notified_at;
    END IF;
END $$;

-- 회차 쪽지 미리 만들기: 1~9화 쪽지를 회차를 끝내기 전에 만들어 두고, 회차 완료 요청에서 도착(delivered_at)시킨다.
-- 도착 전 쪽지는 앱에 보이지 않는다. 기존 회차 쪽지는 만들어질 때 바로 보였으므로 생성 시각으로 채운다.
-- Flyway가 Hibernate보다 먼저 돌므로 컬럼도 여기서 더한다. 빈 DB(CI)는 조건부 no-op이다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'partner_letters') THEN
        RETURN;
    END IF;

    ALTER TABLE partner_letters ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP(6);
    UPDATE partner_letters SET delivered_at = created_at
    WHERE episode_order IS NOT NULL AND delivered_at IS NULL AND message IS NOT NULL;
END $$;

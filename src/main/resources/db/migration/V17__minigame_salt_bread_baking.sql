-- 새 미니게임 "소금빵굽기"(code salt_bread_baking)를 등록한다. 프론트는 code로 게임을 찾는다.
--
-- minigames는 Hibernate ddl-auto가 만들고 SeedDataLoader.seedMinigames()가 테이블이 비어 있을 때만 시드하는 테이블이라,
-- 이미 시드된 환경(스테이징·운영)에는 시드 코드만으로 새 게임이 들어가지 않는다. V4처럼 테이블이 있을 때만 동작하고,
-- 빈 DB(CI)에서는 no-op이다(이후 시드가 세 게임을 모두 넣는다). code가 이미 있으면 넣지 않아 재실행해도 중복되지 않는다.
-- code/title/description/reward_token 값은 SeedDataLoader.seedMinigames()와 반드시 같게 유지한다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'minigames')
       AND NOT EXISTS (SELECT 1 FROM minigames WHERE code = 'salt_bread_baking') THEN
        INSERT INTO minigames (code, title, description, thumbnail_url, reward_token)
        VALUES ('salt_bread_baking', '소금빵굽기', '소금빵 3개를 만들어 파는 미니게임', NULL, 1);
    END IF;
END $$;

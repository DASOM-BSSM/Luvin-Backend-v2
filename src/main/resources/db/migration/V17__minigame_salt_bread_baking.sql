-- 새 미니게임 "소금빵굽기"(code salt_bread_baking)를 등록한다. 프론트는 code로 게임을 찾는다.
--
-- minigames는 Hibernate ddl-auto가 만들고 SeedDataLoader.seedMinigames()가 테이블이 비어 있을 때만 시드하는 테이블이라,
-- 이미 시드된 환경(스테이징·운영)에는 시드 코드만으로 새 게임이 들어가지 않는다. V4처럼 테이블이 있을 때만 동작하고,
-- 빈 DB(CI)에서는 no-op이다(이후 시드가 세 게임을 모두 넣는다). code가 이미 있으면 넣지 않아 재실행해도 중복되지 않는다.
-- code/title/description/reward_token 값은 SeedDataLoader.seedMinigames()와 반드시 같게 유지한다.
--
-- 테이블 존재 확인과 minigames 조회를 한 IF 조건에 AND로 묶으면 빈 DB에서 minigames 참조가 먼저 해석돼
-- "relation does not exist"로 실패한다(AND는 실행 계획 단계의 참조 해석을 건너뛰지 않는다). 그래서 V4처럼 IF를 나눈다 —
-- 안쪽 문장은 바깥 조건이 참일 때만 해석·실행된다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'minigames') THEN
        IF NOT EXISTS (SELECT 1 FROM minigames WHERE code = 'salt_bread_baking') THEN
            INSERT INTO minigames (code, title, description, thumbnail_url, reward_token)
            VALUES ('salt_bread_baking', '소금빵굽기', '소금빵 3개를 만들어 파는 미니게임', NULL, 1);
        END IF;
    END IF;
END $$;

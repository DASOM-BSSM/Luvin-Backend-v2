-- 다시 굽기(reroll) 제거. 엔티티와 API는 지웠지만 데이터는 남겨 두고 사용 중단만 표시한다(drop하지 않는다).
-- 두 테이블은 Hibernate ddl-auto가 만들던 테이블이라, 엔티티가 없어진 지금은 아무 코드도 읽거나 쓰지 않는다.
--
-- - ai_reroll_requests: 러빈지옥 2·4화 1:1 상대 다시 굽기 job 기록
-- - rebake_messages: 예전 simulation 모듈의 다시 굽기 대화
--
-- ai_idempotency_keys.action_type의 'EPISODE_REROLL'은 기존 row가 있어 CHECK 제약과 enum 값을 그대로 둔다.
--
-- 빈 DB(CI)에는 두 테이블이 없으므로(엔티티가 없어 Hibernate도 만들지 않는다) 조건부로 no-op이다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_reroll_requests') THEN
        COMMENT ON TABLE ai_reroll_requests IS 'DEPRECATED (V9): 다시 굽기 제거로 사용 중단. 코드에서 읽거나 쓰지 않는다.';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'rebake_messages') THEN
        COMMENT ON TABLE rebake_messages IS 'DEPRECATED (V9): 다시 굽기 제거로 사용 중단. 코드에서 읽거나 쓰지 않는다.';
    END IF;
END $$;

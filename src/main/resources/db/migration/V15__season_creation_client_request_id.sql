-- 새로운 시즌 만들기(POST /api/ai/seasons/next)의 멱등 키: 같은 clientRequestId로 다시 오면 그 요청이 만든 시즌을
-- 그대로 돌려준다. 시즌 생성 입력(ai_season_creation_inputs)에 요청 id를 둔다. 빈 DB(CI)는 조건부 no-op이다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_season_creation_inputs') THEN
        RETURN;
    END IF;

    ALTER TABLE ai_season_creation_inputs ADD COLUMN IF NOT EXISTS client_request_id VARCHAR(100);
END $$;

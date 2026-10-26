-- 공통 데이터 계약 v7 7장 "배경별 말투": 관계 기록(ai_bread_relations)마다 내 빵과의 말투(speech_level)를 둔다.
-- Flyway가 Hibernate보다 먼저 돌므로 기존 테이블에는 여기서 컬럼·CHECK를 더하고, 진행 중인 시즌의 기존 행은
-- 시즌 시작 값(school이면 CASUAL, 나머지 배경은 FORMAL)으로 채운다. 빈 DB(CI)는 조건부 no-op이다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_bread_relations') THEN
        RETURN;
    END IF;

    ALTER TABLE ai_bread_relations ADD COLUMN IF NOT EXISTS speech_level VARCHAR(10);
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ai_bread_relations_speech_level_check') THEN
        ALTER TABLE ai_bread_relations ADD CONSTRAINT ai_bread_relations_speech_level_check
            CHECK (speech_level IN ('FORMAL', 'CASUAL'));
    END IF;

    UPDATE ai_bread_relations r
    SET speech_level = CASE WHEN s.setting = 'SCHOOL' THEN 'CASUAL' ELSE 'FORMAL' END
    FROM ai_seasons s
    WHERE r.season_id = s.id AND r.speech_level IS NULL;
END $$;

-- 선택 처리(POST .../choices)를 접수 후 폴링으로 바꾼다: 선택은 바로 저장하고, 결과 장면(choice_result)은 백그라운드에서
-- 만든다. 그래서 result_blocks는 만드는 동안 비어 있을 수 있고, 생성 상태·회차·접수 시각·실패 코드를 둔다.
-- 기존 선택은 모두 결과 장면까지 저장된 것이라 READY로 채운다. result_status는 enum 컬럼이라 V6처럼 CHECK도 직접 만든다.
-- 빈 DB(CI)는 테이블이 없어 조건부 no-op이고, 이후 Hibernate가 엔티티 정의 그대로 만든다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_season_choices') THEN
        RETURN;
    END IF;

    ALTER TABLE ai_season_choices ALTER COLUMN result_blocks DROP NOT NULL;
    ALTER TABLE ai_season_choices ADD COLUMN IF NOT EXISTS result_status VARCHAR(20);
    ALTER TABLE ai_season_choices ADD COLUMN IF NOT EXISTS result_round INT;
    ALTER TABLE ai_season_choices ADD COLUMN IF NOT EXISTS result_requested_at TIMESTAMP;
    ALTER TABLE ai_season_choices ADD COLUMN IF NOT EXISTS result_error_code VARCHAR(100);
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ai_season_choices_result_status_check') THEN
        ALTER TABLE ai_season_choices ADD CONSTRAINT ai_season_choices_result_status_check
            CHECK (result_status IN ('GENERATING', 'READY', 'FAILED'));
    END IF;
    UPDATE ai_season_choices SET result_status = 'READY', result_round = 1 WHERE result_status IS NULL;
END $$;

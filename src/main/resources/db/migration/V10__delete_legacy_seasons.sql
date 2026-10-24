-- 러빈지옥 시즌 정책(공통 데이터 계약 0장): 사용자마다 시즌은 1개이고 장면 블록 1~10화뿐이다.
-- 기존 5화(legacy) 시즌은 모두 지운다 — 되돌릴 수 없다.
--
-- legacy 시즌 = 기존 5화 흐름으로 회차 생성을 요청한 적이 있고(ai_episode_progresses) 새 흐름을 시작하지 않은(setting null)
-- 시즌. 새로 만들었지만 아직 1화를 열지 않은 시즌(진행 기록 없음)은 남긴다.
--
-- 시즌을 참조하는 모든 행을 먼저 지우고, 그 사용자의 시즌 생성 입력(ai_season_creation_inputs)과 AI 호출
-- idempotency key도 지운다 — 남겨 두면 다시 시즌을 만들 때 같은 key로 AI가 예전 시즌을 그대로 돌려준다.
-- 지워진 사용자는 설문 결과로 새 시즌을 만든다(POST /api/ai/seasons/from-survey).
--
-- 빈 DB(CI)에는 테이블이 없어 조건부로 no-op이다.
DO $$
DECLARE
    child_table text;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_seasons')
       OR NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_episode_progresses') THEN
        RETURN;
    END IF;

    CREATE TEMP TABLE legacy_seasons ON COMMIT DROP AS
        SELECT s.id, s.member_id, s.season_number
        FROM ai_seasons s
        WHERE s.setting IS NULL
          AND EXISTS (SELECT 1 FROM ai_episode_progresses p WHERE p.season_id = s.id);

    FOREACH child_table IN ARRAY ARRAY[
        'ai_episode_progresses', 'ai_selections', 'ai_episode_feedbacks', 'ai_episode_message_likes',
        'partner_letters', 'ai_reroll_requests', 'ai_season_choices', 'ai_bread_relations', 'ai_episode_contents',
        'ai_season_story_reports', 'ai_story_feedbacks', 'ai_story_message_likes', 'ai_episode_unlocks',
        'ai_season_characters']
    LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = child_table) THEN
            EXECUTE format('DELETE FROM %I WHERE season_id IN (SELECT id FROM legacy_seasons)', child_table);
        END IF;
    END LOOP;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_season_creation_inputs') THEN
        DELETE FROM ai_season_creation_inputs i
        USING legacy_seasons l
        WHERE i.member_id = l.member_id AND i.season_number = l.season_number;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_idempotency_keys') THEN
        DELETE FROM ai_idempotency_keys k WHERE k.member_id IN (SELECT member_id FROM legacy_seasons);
    END IF;

    DELETE FROM ai_seasons WHERE id IN (SELECT id FROM legacy_seasons);
END $$;

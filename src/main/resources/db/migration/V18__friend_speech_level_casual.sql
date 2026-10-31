-- reconstruction.v22 역할별 말투: 친구(cast_role FRIEND)는 배경과 상관없이 내 빵과 반말(CASUAL)이다.
-- 진행 중인 시즌에서 실제 친구 관계(관계의 season_id·target_bread_id = 출연진의 season_id·character_id)만 CASUAL로
-- 교정한다. 끝난 시즌·이미 저장된 메시지·리포트는 바꾸지 않는다. Flyway가 Hibernate보다 먼저 돌므로 빈 DB(CI)는
-- 조건부 no-op이다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_bread_relations')
        OR NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_season_characters') THEN
        RETURN;
    END IF;

    UPDATE ai_bread_relations r
    SET speech_level = 'CASUAL'
    FROM ai_season_characters c, ai_seasons s
    WHERE c.season_id = r.season_id
      AND c.character_id = r.target_bread_id
      AND c.cast_role = 'FRIEND'
      AND s.id = r.season_id
      AND s.member_id = r.member_id
      AND s.status = 'IN_PROGRESS'
      AND r.speech_level IS DISTINCT FROM 'CASUAL';
END $$;

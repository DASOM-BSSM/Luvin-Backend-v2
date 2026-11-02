-- 그룹 질문: 그룹당 하루(KST) 1개 → KST 정각 칸(예: 09:00~09:59)마다 1개.
-- slot_start_at = 질문을 만든 시각을 KST 정각으로 내린 값. 기존 질문은 created_at으로 채운다(하루 1개였으므로 겹치지 않는다).
-- question_date는 칸의 날짜(KST)로 계속 채워 둔다(응답 questionDate 하위 호환).
-- room_question은 V12가 만들므로 빈 DB(CI)에도 있다. 조건 없이 바꾼다.
ALTER TABLE room_question ADD COLUMN IF NOT EXISTS slot_start_at TIMESTAMPTZ;
UPDATE room_question
SET slot_start_at = date_trunc('hour', created_at AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'Asia/Seoul'
WHERE slot_start_at IS NULL;
ALTER TABLE room_question ALTER COLUMN slot_start_at SET NOT NULL;

ALTER TABLE room_question DROP CONSTRAINT IF EXISTS uk_room_question_room_date;
ALTER TABLE room_question ADD CONSTRAINT uk_room_question_room_slot UNIQUE (room_id, slot_start_at);

DROP INDEX IF EXISTS ix_room_question_room_created;

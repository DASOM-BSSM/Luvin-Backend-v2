-- 러빈지옥 운영 설정(재배포 없이 바꾸는 값). 지금은 소금빵 회차 열기 대기 시간(episode-unlock-cooldown) 하나만 둔다.
-- 행이 없으면 app.ai.story.episode-unlock-cooldown(환경변수, 없으면 코드 기본 3일)을 쓴다.
-- 값은 ISO-8601 기간 문자열(예: PT24H)로 저장한다.
CREATE TABLE IF NOT EXISTS ai_story_settings (
    setting_key   VARCHAR(100) NOT NULL PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_at    TIMESTAMP(6) NOT NULL
);

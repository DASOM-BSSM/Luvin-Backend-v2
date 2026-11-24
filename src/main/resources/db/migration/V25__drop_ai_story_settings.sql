-- 소금빵 회차 열기 대기가 "앞 회차 완료 다음 자정(Asia/Seoul)"으로 고정되어, 대기 시간을 바꾸던 설정 테이블(V24)을 지운다.
DROP TABLE IF EXISTS ai_story_settings;

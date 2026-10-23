# Spring → FastAPI 요청 예시 (공통 데이터 계약 5장)

Spring이 FastAPI story endpoint에 실제로 보내는 요청 JSON입니다. `AiStoryHttpContractIntegrationTest`가 1~10화를
끝까지 돌리며 실제 직렬화 결과를 `build/story-contract-examples/`에 남기고, 그 결과를 복사해 둔 것입니다.
대사·요약 문구는 테스트용 stub이 만든 값이라 의미는 없고, 필드 구성과 값의 형식을 보면 됩니다.

| 파일 | endpoint | 계약 |
|---|---|---|
| stage-intro.json | POST /luvin/stage-intro | 5-1 |
| episode.json | POST /luvin/episode (1화) | 5-2 |
| choice-result.json | POST /luvin/choice-result (3화 ①) | 5-3 |
| interview.json | POST /luvin/interview (10화) | 5-4 |
| summarize.json | POST /luvin/summarize (1화 완료) | 5-5 |
| episode-note.json | POST /luvin/episode-note (10화, episodeSummaries 포함) | 5-6 |
| season-report.json | POST /luvin/season-report | 5-7 |

계약과 다른 점: episode-note의 `episodeSummaries`는 계약 5-6에 아직 없습니다. 10화(시즌 종료 쪽지)에만 1~10화 요약을
담아 보내고, 그 외 회차에는 필드 자체가 없습니다(AI 문서 P6). 계약 문서에 추가가 필요합니다.

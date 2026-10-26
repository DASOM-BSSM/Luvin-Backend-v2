package com.luvin.ai.client.emodi;

import java.util.List;
import java.util.Map;

/**
 * 감정일기(Emodi) FastAPI endpoint 요청·응답(camelCase, aiStoryRestClient와 같은 직렬화). 경로·필드 설명은
 * docs/emodi-ai-contract.md에 있다. 다른 사용자의 글은 어떤 요청에도 넣지 않는다.
 */
public final class EmodiTypes {

    private EmodiTypes() {
    }

    /** POST /emodi/daily-question: 전체 사용자 공통 O/X 질문 하나. date는 KST 날짜(yyyy-MM-dd). */
    public record DailyQuestionRequest(String date, List<String> recentQuestions) {
    }

    /** POST /emodi/room-question: 그룹 질문 하나. 그룹 맥락은 이름·멤버 수·최근 질문뿐이다(일기 본문 없음). */
    public record RoomQuestionRequest(String roomName, int memberCount, List<String> recentQuestions) {
    }

    /** 두 질문 생성 endpoint 공통 응답. */
    public record QuestionResponse(String content) {
    }

    /** sourceType: DIARY | COMMENT | ROOM_ANSWER. text는 내가 쓴 글 원문(추출에만 쓰고 결과에 그대로 옮기지 않는다). */
    public record WrittenText(String sourceType, String text) {
    }

    /** POST /emodi/speech-style. previousStyle은 지금까지 저장된 말투 요약(없으면 null). */
    public record SpeechStyleRequest(List<WrittenText> texts, Map<String, Object> previousStyle) {
    }

    public record SpeechStyleResponse(Map<String, Object> speechStyle) {
    }

    /** sourceType: BALANCE_ANSWER(answer는 "O"/"X") | ROOM_ANSWER(answer는 자유 답). */
    public record AnsweredQuestion(String sourceType, String question, String answer) {
    }

    /** POST /emodi/personality-signal. axes는 변화량을 낼 수 있는 성격 축 key 목록. */
    public record PersonalitySignalRequest(List<AnsweredQuestion> answers, List<String> axes) {
    }

    /** 축 key → 이번 답들에서 읽힌 변화량(1~100 척도 기준, 부호 있음). 근거가 없는 축은 빼거나 0. */
    public record PersonalitySignalResponse(Map<String, Double> deltas) {
    }
}

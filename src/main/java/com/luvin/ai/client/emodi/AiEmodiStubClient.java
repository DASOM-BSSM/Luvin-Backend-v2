package com.luvin.ai.client.emodi;

import com.luvin.ai.client.emodi.EmodiTypes.PersonalitySignalRequest;
import com.luvin.ai.client.emodi.EmodiTypes.PersonalitySignalResponse;
import com.luvin.ai.client.emodi.EmodiTypes.QuestionResponse;
import com.luvin.ai.client.emodi.EmodiTypes.RoomQuestionRequest;
import com.luvin.ai.client.emodi.EmodiTypes.SpeechStyleRequest;
import com.luvin.ai.client.emodi.EmodiTypes.SpeechStyleResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * FastAPI emodi endpoint가 연결되기 전 stub. 질문은 준비해 둔 질문({@link EmodiQuestionPool})에서 고르고,
 * 학습 추출은 결과 없음으로 돌려준다 — 가짜 말투·성격을 저장하지 않고, 신호는 http로 바꾼 뒤 처리되도록 남긴다.
 */
@Component
// 기본값을 두지 않는다 — 설정이 빠진 환경(스테이징)이 조용히 stub으로 돌며 임시 문구를 정상 결과로 저장한 적이 있다.
// app.ai.emodi.client(stub|http)가 없으면 구현 빈이 없어 기동이 실패한다.
@ConditionalOnProperty(name = "app.ai.emodi.client", havingValue = "stub")
public class AiEmodiStubClient implements AiEmodiClient {

    @Override
    public QuestionResponse roomQuestion(String ownerSubject, RoomQuestionRequest request) {
        return new QuestionResponse(EmodiQuestionPool.roomFor(request.roomName().hashCode(), LocalDate.now(),
                request.recentQuestions()));
    }

    @Override
    public Optional<SpeechStyleResponse> extractSpeechStyle(String ownerSubject, SpeechStyleRequest request) {
        return Optional.empty();
    }

    @Override
    public Optional<PersonalitySignalResponse> extractPersonalitySignal(String ownerSubject,
                                                                       PersonalitySignalRequest request) {
        return Optional.empty();
    }
}

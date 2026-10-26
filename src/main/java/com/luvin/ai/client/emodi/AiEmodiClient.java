package com.luvin.ai.client.emodi;

import com.luvin.ai.client.emodi.EmodiTypes.PersonalitySignalRequest;
import com.luvin.ai.client.emodi.EmodiTypes.PersonalitySignalResponse;
import com.luvin.ai.client.emodi.EmodiTypes.QuestionResponse;
import com.luvin.ai.client.emodi.EmodiTypes.RoomQuestionRequest;
import com.luvin.ai.client.emodi.EmodiTypes.SpeechStyleRequest;
import com.luvin.ai.client.emodi.EmodiTypes.SpeechStyleResponse;

import java.util.Optional;

/**
 * 감정일기 질문 생성·학습 추출용 FastAPI endpoint. app.ai.emodi.client=stub(기본)이면 {@link AiEmodiStubClient},
 * http면 {@link AiEmodiHttpClient}가 쓰인다. 실패는 AiServiceException 계열로 던진다.
 * 학습 추출은 결과가 없을 수 있다(Optional.empty) — 그때 신호는 소비하지 않고 다음 배치에서 다시 시도한다.
 */
public interface AiEmodiClient {

    QuestionResponse roomQuestion(String ownerSubject, RoomQuestionRequest request);

    Optional<SpeechStyleResponse> extractSpeechStyle(String ownerSubject, SpeechStyleRequest request);

    Optional<PersonalitySignalResponse> extractPersonalitySignal(String ownerSubject, PersonalitySignalRequest request);
}

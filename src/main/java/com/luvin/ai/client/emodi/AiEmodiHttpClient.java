package com.luvin.ai.client.emodi;

import com.luvin.ai.client.AiServiceClient;
import com.luvin.ai.client.emodi.EmodiTypes.DailyQuestionRequest;
import com.luvin.ai.client.emodi.EmodiTypes.PersonalitySignalRequest;
import com.luvin.ai.client.emodi.EmodiTypes.PersonalitySignalResponse;
import com.luvin.ai.client.emodi.EmodiTypes.QuestionResponse;
import com.luvin.ai.client.emodi.EmodiTypes.RoomQuestionRequest;
import com.luvin.ai.client.emodi.EmodiTypes.SpeechStyleRequest;
import com.luvin.ai.client.emodi.EmodiTypes.SpeechStyleResponse;
import com.luvin.ai.config.AiStoryConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/** FastAPI emodi endpoint 호출(camelCase). 부작용 없는 생성이라 Idempotency-Key는 호출마다 새로 만든다. */
@Component
@ConditionalOnProperty(name = "app.ai.emodi.client", havingValue = "http")
public class AiEmodiHttpClient implements AiEmodiClient {

    static final String DAILY_QUESTION_PATH = "/emodi/daily-question";
    static final String ROOM_QUESTION_PATH = "/emodi/room-question";
    static final String SPEECH_STYLE_PATH = "/emodi/speech-style";
    static final String PERSONALITY_SIGNAL_PATH = "/emodi/personality-signal";

    private final AiServiceClient aiServiceClient;
    private final RestClient restClient;

    public AiEmodiHttpClient(AiServiceClient aiServiceClient,
                             @Qualifier("aiStoryRestClient") RestClient restClient) {
        this.aiServiceClient = aiServiceClient;
        this.restClient = restClient;
    }

    @Override
    public QuestionResponse dailyQuestion(String ownerSubject, DailyQuestionRequest request) {
        return post(DAILY_QUESTION_PATH, ownerSubject, request, QuestionResponse.class);
    }

    @Override
    public QuestionResponse roomQuestion(String ownerSubject, RoomQuestionRequest request) {
        return post(ROOM_QUESTION_PATH, ownerSubject, request, QuestionResponse.class);
    }

    @Override
    public Optional<SpeechStyleResponse> extractSpeechStyle(String ownerSubject, SpeechStyleRequest request) {
        return Optional.ofNullable(post(SPEECH_STYLE_PATH, ownerSubject, request, SpeechStyleResponse.class));
    }

    @Override
    public Optional<PersonalitySignalResponse> extractPersonalitySignal(String ownerSubject,
                                                                       PersonalitySignalRequest request) {
        return Optional.ofNullable(post(PERSONALITY_SIGNAL_PATH, ownerSubject, request,
                PersonalitySignalResponse.class));
    }

    private <T> T post(String path, String ownerSubject, Object request, Class<T> type) {
        return aiServiceClient.post(restClient, AiStoryConfig.STORY_OBJECT_MAPPER, path, ownerSubject,
                UUID.randomUUID(), request, type);
    }
}

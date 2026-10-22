package com.luvin.ai.service.story;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luvin.ai.client.story.StoryContextDto;
import com.luvin.ai.repository.AiSeasonCreationInputRepository;
import com.luvin.dailyquestion.repository.DailyQuestionAnswerRepository;
import com.luvin.diary.repository.DiaryRepository;
import com.luvin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 내 빵 학습 데이터: 시즌을 만들 때 쓴 설문 성향 점수, 빵 타입, 최근 매시간 질문 답변과 감정일기.
 * 일기 본문은 앞부분만 잘라 보낸다. AI 서비스(우리 FastAPI) 밖으로는 나가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AiLearningDataProvider {

    static final int DIARY_LIMIT = 5;
    static final int DIARY_EXCERPT_MAX = 300;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final UserRepository userRepository;
    private final AiSeasonCreationInputRepository creationInputRepository;
    private final DailyQuestionAnswerRepository dailyQuestionAnswerRepository;
    private final DiaryRepository diaryRepository;

    @Transactional(readOnly = true)
    public StoryContextDto.LearningData load(Long memberId, int seasonNumber) {
        String personalityType = userRepository.findById(memberId).map(u -> u.getPersonalityType()).orElse(null);
        Object traits = creationInputRepository.findByMemberIdAndSeasonNumber(memberId, seasonNumber)
                .map(input -> traitsOf(input.getProfileJson()))
                .orElse(null);
        List<StoryContextDto.DailyAnswer> answers = dailyQuestionAnswerRepository
                .findTop10ByMemberIdOrderByAnsweredAtDesc(memberId).stream()
                .map(a -> new StoryContextDto.DailyAnswer(a.getDailyQuestion().getContent(),
                        a.getSelectedOption().getContent(), a.getAnsweredAt()))
                .toList();
        List<StoryContextDto.DiaryExcerpt> diaries = diaryRepository
                .findAllByUserIdOrderByCreatedAtDesc(memberId, PageRequest.of(0, DIARY_LIMIT)).stream()
                .map(d -> new StoryContextDto.DiaryExcerpt(d.getTitle(), excerpt(d.getContent()), d.getCreatedAt()))
                .toList();
        return new StoryContextDto.LearningData(personalityType, traits, answers, diaries);
    }

    private static Object traitsOf(String profileJson) {
        try {
            JsonNode traits = JSON.readTree(profileJson).get("traits");
            return traits == null ? null : JSON.treeToValue(traits, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String excerpt(String content) {
        if (content == null || content.length() <= DIARY_EXCERPT_MAX) {
            return content;
        }
        return content.substring(0, DIARY_EXCERPT_MAX);
    }
}

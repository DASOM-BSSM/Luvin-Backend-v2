package com.luvin.ai.service.story;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.repository.AiSeasonCreationInputRepository;
import com.luvin.dailyquestion.repository.DailyQuestionAnswerRepository;
import com.luvin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 내 빵 학습 데이터(계약 GenerationContext.learnedProfile). Spring은 LLM을 부르지 않으므로 요약(summary)은
 * 설문 빵 타입과 두드러진 성향 점수로 코드가 만든다. 매시간 질문 답변은 최근 몇 개를 "질문 → 답"으로 보낸다.
 * 1:1 장면 피드백·좋아요는 {@link AiStoryStateWriter}가 시즌별로 따로 읽어 채운다.
 */
@Component
@RequiredArgsConstructor
public class AiLearningDataProvider {

    static final int RECENT_ANSWER_LIMIT = 5;
    /** 성향 점수(1~100)가 이 이상이면 "높은 편", 이 이하면 "낮은 편"으로 요약에 넣는다. */
    static final int HIGH_TRAIT = 65;
    static final int LOW_TRAIT = 35;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final UserRepository userRepository;
    private final AiSeasonCreationInputRepository creationInputRepository;
    private final DailyQuestionAnswerRepository dailyQuestionAnswerRepository;
    private final AiBreadCatalog breadCatalog;

    /** feedbacks/likedMessages는 빈 목록으로 돌려준다 — 호출부가 시즌 기록으로 채운다. */
    @Transactional(readOnly = true)
    public StoryTypes.LearnedProfile load(Long memberId, int seasonNumber) {
        String personalityType = userRepository.findById(memberId).map(u -> u.getPersonalityType()).orElse(null);
        Map<String, Integer> traits = creationInputRepository.findByMemberIdAndSeasonNumber(memberId, seasonNumber)
                .map(input -> traitsOf(input.getProfileJson()))
                .orElse(Map.of());
        List<String> answers = dailyQuestionAnswerRepository.findTop10ByMemberIdOrderByAnsweredAtDesc(memberId).stream()
                .limit(RECENT_ANSWER_LIMIT)
                .map(a -> a.getDailyQuestion().getContent() + " → " + a.getSelectedOption().getContent())
                .toList();
        return new StoryTypes.LearnedProfile(summary(personalityType, traits), answers, List.of(), List.of());
    }

    /** 예: "소금빵 타입. 관계 불안도·확신 요구도가 높은 편이고 갈등 직면성이 낮은 편이다." */
    String summary(String personalityType, Map<String, Integer> traits) {
        String bread = breadCatalog.breadLabel(personalityType);
        List<String> high = labels(traits, true);
        List<String> low = labels(traits, false);
        StringBuilder text = new StringBuilder(bread == null ? "빵 타입 미정." : bread + " 타입.");
        if (!high.isEmpty()) {
            text.append(' ').append(String.join("·", high)).append("이(가) 높은 편");
        }
        if (!low.isEmpty()) {
            text.append(high.isEmpty() ? " " : "이고 ").append(String.join("·", low)).append("이(가) 낮은 편");
        }
        if (!high.isEmpty() || !low.isEmpty()) {
            text.append("이다.");
        }
        return text.toString();
    }

    /** 가장 두드러진 두 개(높으면 큰 순, 낮으면 작은 순). */
    private List<String> labels(Map<String, Integer> traits, boolean high) {
        List<Map.Entry<String, Integer>> picked = new ArrayList<>(traits.entrySet().stream()
                .filter(e -> high ? e.getValue() >= HIGH_TRAIT : e.getValue() <= LOW_TRAIT)
                .toList());
        Comparator<Map.Entry<String, Integer>> byScore = Map.Entry.comparingByValue();
        picked.sort(high ? byScore.reversed() : byScore);
        return picked.stream().limit(2)
                .map(e -> breadCatalog.dimensionLabel(e.getKey()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** 시즌 생성 입력(CharacterProfileDto JSON)의 traits는 camelCase key다 — 설문 차원 key(snake_case)로 바꾼다. */
    private static Map<String, Integer> traitsOf(String profileJson) {
        try {
            JsonNode traits = JSON.readTree(profileJson).get("traits");
            if (traits == null) {
                return Map.of();
            }
            Map<String, Integer> scores = new java.util.LinkedHashMap<>();
            traits.fields().forEachRemaining(e -> scores.put(
                    e.getKey().replaceAll("([A-Z])", "_$1").toLowerCase(), e.getValue().asInt()));
            return scores;
        } catch (Exception e) {
            return Map.of();
        }
    }
}

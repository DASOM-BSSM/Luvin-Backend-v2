package com.luvin.survey.definition;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * survey_config.json(snake_case wire, Python reference와 동일 파일)을 읽어 {@link SurveyDefinitionConfig}로
 * 파싱하고, Python classifier.py의 validate_config()와 동일한 구조적 불변식을 검증한다.
 * 검증에 실패한 release는 절대 활성화하지 않는다.
 */
public final class SurveyDefinitionConfigLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private SurveyDefinitionConfigLoader() {
    }

    public static SurveyDefinitionConfig loadFromClasspath(String resourcePath) {
        try (InputStream in = SurveyDefinitionConfigLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new SurveyDefinitionInvalidException("설문 definition 리소스를 찾을 수 없습니다: " + resourcePath);
            }
            return parse(in);
        } catch (IOException e) {
            throw new SurveyDefinitionInvalidException("설문 definition 파싱에 실패했습니다: " + resourcePath + " (" + e.getMessage() + ")");
        }
    }

    /** DB에 저장된 config_json 등 이미 메모리에 있는 원문을 파싱할 때 사용한다. */
    public static SurveyDefinitionConfig parse(String json) {
        try {
            SurveyDefinitionConfig config = OBJECT_MAPPER.readValue(json, SurveyDefinitionConfig.class);
            validate(config);
            return config;
        } catch (IOException e) {
            throw new SurveyDefinitionInvalidException("설문 definition 파싱에 실패했습니다: " + e.getMessage());
        }
    }

    private static SurveyDefinitionConfig parse(InputStream in) throws IOException {
        try {
            SurveyDefinitionConfig config = OBJECT_MAPPER.readValue(in, SurveyDefinitionConfig.class);
            validate(config);
            return config;
        } catch (IOException e) {
            throw new SurveyDefinitionInvalidException("설문 definition 파싱에 실패했습니다: " + e.getMessage());
        }
    }

    public static void validate(SurveyDefinitionConfig config) {
        require(config.dimensions().size() == 20, "dimensions는 20개여야 합니다.");
        long coreCount = config.dimensions().values().stream().filter(SurveyDefinitionDimension::isCore).count();
        require(coreCount == 13, "core dimension은 13개여야 합니다.");
        require(config.questions().size() == 20, "questions는 20개여야 합니다.");

        Set<String> questionIds = new HashSet<>();
        Set<String> primaryDimensions = new HashSet<>();
        Set<String> allAnswerIds = new HashSet<>();
        for (SurveyDefinitionQuestion question : config.questions()) {
            require(questionIds.add(question.id()), "question id가 중복되었습니다: " + question.id());
            require(config.dimensions().containsKey(question.primaryDimension()),
                    "알 수 없는 primary_dimension입니다: " + question.primaryDimension());
            primaryDimensions.add(question.primaryDimension());
            require(question.answers().size() == 3, "각 질문은 답변 3개를 가져야 합니다: " + question.id());

            for (SurveyDefinitionAnswer answer : question.answers()) {
                require(allAnswerIds.add(answer.id()), "answer id가 중복되었습니다: " + answer.id());
                SurveyDefinitionEvidence primaryEvidence = answer.evidence().get(question.primaryDimension());
                require(primaryEvidence != null && primaryEvidence.weight() == 2,
                        "주 측정 지표의 weight는 2여야 합니다: " + answer.id());
                for (var entry : answer.evidence().entrySet()) {
                    require(config.dimensions().containsKey(entry.getKey()),
                            "알 수 없는 evidence dimension입니다: " + entry.getKey());
                    int value = entry.getValue().value();
                    require(value == -1 || value == 0 || value == 1,
                            "evidence value는 -1/0/1이어야 합니다: " + answer.id() + "/" + entry.getKey());
                    require(entry.getValue().weight() > 0,
                            "evidence weight는 양수여야 합니다: " + answer.id() + "/" + entry.getKey());
                }
            }
        }
        require(primaryDimensions.equals(config.dimensions().keySet()),
                "모든 dimension은 하나 이상의 질문에서 주 측정 지표로 쓰여야 합니다.");
        require(config.priorWeight() > 0, "prior_weight는 양수여야 합니다.");

        require(config.profiles().size() == 8, "profiles는 8개여야 합니다.");
        Set<String> profileIds = new HashSet<>();
        for (SurveyDefinitionProfile profile : config.profiles()) {
            require(profileIds.add(profile.id()), "profile id가 중복되었습니다: " + profile.id());
            require(!profile.features().isEmpty(), "profile은 feature를 하나 이상 가져야 합니다: " + profile.id());
            for (var entry : profile.features().entrySet()) {
                require(config.dimensions().containsKey(entry.getKey()),
                        "알 수 없는 profile feature dimension입니다: " + entry.getKey());
                SurveyDefinitionProfileFeature feature = entry.getValue();
                require(1 <= feature.low() && feature.low() <= feature.high() && feature.high() <= 100,
                        "profile feature range가 1..100을 벗어났습니다: " + profile.id() + "/" + entry.getKey());
                require(feature.weight() > 0, "profile feature weight는 양수여야 합니다: " + profile.id() + "/" + entry.getKey());
            }
        }
        require(new HashSet<>(config.tieBreakOrder()).size() == 8, "tie_break_order는 8개의 서로 다른 값이어야 합니다.");
        require(new HashSet<>(config.tieBreakOrder()).equals(profileIds),
                "tie_break_order는 profile id 집합과 정확히 일치해야 합니다.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new SurveyDefinitionInvalidException(message);
        }
    }
}

package com.luvin.survey.scoring;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luvin.survey.definition.SurveyDefinitionConfig;
import com.luvin.survey.definition.SurveyDefinitionConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * luvin-survey-change-plan/reference의 Python classifier.py 참조 구현이 만든 golden_fixtures.json
 * 73개 케이스와 Java 이식(SurveyScorer + BreadTypeClassifier)의 결과를 전부 대조한다.
 * 표시값(2/6자리 반올림)뿐 아니라 evidence_weight/observations/direction_conflict, distances 전체,
 * reason_evidence 순서까지 정확히 일치해야 한다 — 이 테스트가 곧 Python-Java parity 검증이다.
 */
class BreadTypeClassificationParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    @Test
    void allGoldenFixturesMatchJavaPort() throws IOException {
        SurveyDefinitionConfig config = SurveyDefinitionConfigLoader
                .loadFromClasspath("surveys/bread_survey.v1-draft/survey_config.json");

        JsonNode root;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("survey/golden_fixtures.json")) {
            assertNotNull(in, "golden_fixtures.json 리소스를 찾을 수 없습니다.");
            root = MAPPER.readTree(in);
        }

        JsonNode cases = root.get("cases");
        assertTrue(cases.isArray() && cases.size() == 73, "golden fixture case 수는 73개여야 합니다.");

        List<Executable> assertions = new ArrayList<>();
        for (JsonNode testCase : cases) {
            String name = testCase.get("name").asText();
            JsonNode request = testCase.get("request");
            JsonNode expected = testCase.get("expected");

            Map<String, String> answers = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = request.get("answers").fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                answers.put(e.getKey(), e.getValue().asText());
            }

            SurveyScoringResult scoring = SurveyScorer.score(config, request.get("survey_version").asText(), answers);
            BreadTypeClassificationResult result = BreadTypeClassifier.classify(config, scoring);

            assertions.add(() -> assertEquals(expected.get("primary_type").asText(), result.primaryType(),
                    name + ": primary_type"));
            assertions.add(() -> assertEquals(expected.get("secondary_type").asText(), result.secondaryType(),
                    name + ": secondary_type"));
            assertions.add(() -> assertEquals(expected.get("mixed").asBoolean(), result.mixed(), name + ": mixed"));
            assertions.add(() -> assertEquals(expected.get("poor_fit").asBoolean(), result.poorFit(), name + ": poor_fit"));
            assertions.add(() -> assertEquals(expected.get("tie").asBoolean(), result.tie(), name + ": tie"));
            assertions.add(() -> assertBigDecimalEquals(expected.get("top_two_distance_gap").decimalValue(),
                    result.topTwoDistanceGap(), name + ": top_two_distance_gap"));

            Iterator<Map.Entry<String, JsonNode>> distanceFields = expected.get("distances").fields();
            while (distanceFields.hasNext()) {
                Map.Entry<String, JsonNode> e = distanceFields.next();
                String profileId = e.getKey();
                BigDecimal expectedDistance = e.getValue().decimalValue();
                assertions.add(() -> {
                    BigDecimal actual = result.distances().get(profileId);
                    assertNotNull(actual, name + ": distances." + profileId + " 누락");
                    assertBigDecimalEquals(expectedDistance, actual, name + ": distances." + profileId);
                });
            }

            addScoreAssertions(assertions, expected.get("core_scores"), scoring, name);
            addScoreAssertions(assertions, expected.get("auxiliary_scores"), scoring, name);

            JsonNode expectedReasons = expected.get("reason_evidence");
            assertions.add(() -> assertEquals(expectedReasons.size(), result.reasonEvidence().size(),
                    name + ": reason_evidence 개수"));
            for (int i = 0; i < expectedReasons.size(); i++) {
                int idx = i;
                JsonNode expectedReason = expectedReasons.get(i);
                assertions.add(() -> {
                    if (idx >= result.reasonEvidence().size()) {
                        fail(name + ": reason_evidence[" + idx + "] 누락");
                        return;
                    }
                    ReasonEvidence actual = result.reasonEvidence().get(idx);
                    assertEquals(expectedReason.get("dimension").asText(), actual.dimension(),
                            name + ": reason_evidence[" + idx + "].dimension");
                    assertEquals(expectedReason.get("direction").asInt(), actual.direction(),
                            name + ": reason_evidence[" + idx + "].direction");
                    List<String> expectedSourceIds = new ArrayList<>();
                    expectedReason.get("source_answer_ids").forEach(n -> expectedSourceIds.add(n.asText()));
                    assertEquals(expectedSourceIds, actual.sourceAnswerIds(),
                            name + ": reason_evidence[" + idx + "].source_answer_ids");
                });
            }
        }

        assertAll(assertions);
    }

    private static void addScoreAssertions(List<Executable> assertions, JsonNode expectedScores,
                                            SurveyScoringResult scoring, String caseName) {
        Iterator<Map.Entry<String, JsonNode>> fields = expectedScores.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            String dimension = e.getKey();
            JsonNode expected = e.getValue();
            assertions.add(() -> {
                DimensionScore actual = scoring.scores().get(dimension);
                assertNotNull(actual, caseName + ": scores." + dimension + " 누락");
                assertBigDecimalEquals(expected.get("score").decimalValue(), actual.score(),
                        caseName + ": scores." + dimension + ".score");
                assertEquals(expected.get("evidence_weight").asInt(), actual.evidenceWeight(),
                        caseName + ": scores." + dimension + ".evidence_weight");
                assertEquals(expected.get("observations").asInt(), actual.observations(),
                        caseName + ": scores." + dimension + ".observations");
                assertEquals(expected.get("direction_conflict").asBoolean(), actual.directionConflict(),
                        caseName + ": scores." + dimension + ".direction_conflict");
            });
        }
    }

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual, String message) {
        assertTrue(expected.compareTo(actual) == 0,
                message + " expected=" + expected + " actual=" + actual);
    }
}

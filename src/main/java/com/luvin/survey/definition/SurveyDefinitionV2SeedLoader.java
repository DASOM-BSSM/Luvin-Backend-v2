package com.luvin.survey.definition;

import com.luvin.survey.domain.SurveyDefinitionV2;
import com.luvin.survey.repository.SurveyDefinitionV2Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 설문 v2 definition을 최초 1회 DB에 import한다. 기존 SeedDataLoader(레거시 설문·오늘의 질문)와
 * 의도적으로 분리했다 — personalityQuestions() 등 레거시 시드 변경이 이 신규 definition에 영향을
 * 주면 안 되고, 반대도 마찬가지다.
 *
 * (survey_version, scoring_version, classification_version) 조합이 이미 있으면 아무 것도 하지 않는다
 * — 과거 definition payload는 절대 덮어쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SurveyDefinitionV2SeedLoader implements ApplicationRunner {

    private static final String RESOURCE_PATH = "surveys/bread_survey.v1-draft/survey_config.json";

    private final SurveyDefinitionV2Repository surveyDefinitionV2Repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        SurveyDefinitionConfig config = SurveyDefinitionConfigLoader.loadFromClasspath(RESOURCE_PATH);

        boolean alreadyImported = surveyDefinitionV2Repository
                .findBySurveyVersionAndScoringVersionAndClassificationVersion(
                        config.surveyVersion(), config.scoringVersion(), config.classificationVersion())
                .isPresent();
        if (alreadyImported) {
            return;
        }

        byte[] rawBytes;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new SurveyDefinitionInvalidException("설문 definition 리소스를 찾을 수 없습니다: " + RESOURCE_PATH);
            }
            rawBytes = in.readAllBytes();
        }

        surveyDefinitionV2Repository.save(new SurveyDefinitionV2(
                UUID.randomUUID(),
                config.surveyVersion(),
                config.scoringVersion(),
                config.classificationVersion(),
                sha256Hex(rawBytes),
                new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8),
                "active"));
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}

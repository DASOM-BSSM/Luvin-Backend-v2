package com.luvin.survey.repository;

import com.luvin.survey.domain.SurveyDefinitionV2;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SurveyDefinitionV2Repository extends JpaRepository<SurveyDefinitionV2, UUID> {
    Optional<SurveyDefinitionV2> findBySurveyVersionAndScoringVersionAndClassificationVersion(
            String surveyVersion, String scoringVersion, String classificationVersion);

    Optional<SurveyDefinitionV2> findFirstByStatusOrderByCreatedAtDesc(String status);
}

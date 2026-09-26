package com.luvin.survey.repository;

import com.luvin.survey.domain.SurveyResultV2;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SurveyResultV2Repository extends JpaRepository<SurveyResultV2, UUID> {
    Optional<SurveyResultV2> findBySubmission_Id(UUID submissionId);

    Optional<SurveyResultV2> findByIdAndSubmission_MemberId(UUID id, Long memberId);
}

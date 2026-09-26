package com.luvin.survey.repository;

import com.luvin.survey.domain.SurveySubmissionV2;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SurveySubmissionV2Repository extends JpaRepository<SurveySubmissionV2, UUID> {
    Optional<SurveySubmissionV2> findByMemberIdAndClientSubmissionId(Long memberId, UUID clientSubmissionId);
}

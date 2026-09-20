package com.luvin.survey.repository;

import com.luvin.survey.domain.SurveyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, Long> {
    long countByMemberId(Long memberId);
    boolean existsByMemberId(Long memberId);
}

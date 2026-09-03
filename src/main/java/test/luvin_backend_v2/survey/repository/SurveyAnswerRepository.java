package com.luvin.survey;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, Long> {
    long countByMemberId(Long memberId);
}

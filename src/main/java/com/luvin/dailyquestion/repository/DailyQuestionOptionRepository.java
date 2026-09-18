package com.luvin.dailyquestion.repository;

import com.luvin.dailyquestion.domain.DailyQuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyQuestionOptionRepository extends JpaRepository<DailyQuestionOption, Long> {
}
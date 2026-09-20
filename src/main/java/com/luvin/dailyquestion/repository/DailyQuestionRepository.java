package com.luvin.dailyquestion.repository;

import com.luvin.dailyquestion.domain.DailyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DailyQuestionRepository extends JpaRepository<DailyQuestion, Long> {
    List<DailyQuestion> findAllByOrderByDailyQuestionIdAsc();
    Optional<DailyQuestion> findFirstByOrderByDailyQuestionIdAsc();
    Optional<DailyQuestion> findFirstByDailyQuestionIdGreaterThanOrderByDailyQuestionIdAsc(Long dailyQuestionId);
}
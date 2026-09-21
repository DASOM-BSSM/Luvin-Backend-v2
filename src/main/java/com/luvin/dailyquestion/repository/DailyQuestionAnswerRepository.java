package com.luvin.dailyquestion.repository;

import com.luvin.dailyquestion.domain.DailyQuestionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DailyQuestionAnswerRepository extends JpaRepository<DailyQuestionAnswer, Long> {
    boolean existsByMemberIdAndDailyQuestion_DailyQuestionId(Long memberId, Long dailyQuestionId);
    Optional<DailyQuestionAnswer> findTopByMemberIdOrderByDailyQuestion_DailyQuestionIdDesc(Long memberId);
    long countByMemberId(Long memberId);

    Optional<DailyQuestionAnswer> findByMemberIdAndDailyQuestion_DailyQuestionId(Long memberId, Long dailyQuestionId);
    long countByDailyQuestion_DailyQuestionId(Long dailyQuestionId);
    long countByDailyQuestion_DailyQuestionIdAndSelectedOption_OptionId(Long dailyQuestionId, Long optionId);
}
package com.luvin.dailyquestion.repository;

import com.luvin.dailyquestion.domain.DailyQuestionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DailyQuestionAnswerRepository extends JpaRepository<DailyQuestionAnswer, Long> {
    boolean existsByMemberIdAndDailyQuestion_DailyQuestionId(Long memberId, Long dailyQuestionId);
    Optional<DailyQuestionAnswer> findTopByMemberIdOrderByDailyQuestion_DailyQuestionIdDesc(Long memberId);
    long countByMemberId(Long memberId);

    Optional<DailyQuestionAnswer> findByMemberIdAndDailyQuestion_DailyQuestionId(Long memberId, Long dailyQuestionId);
    long countByDailyQuestion_DailyQuestionId(Long dailyQuestionId);
    long countByDailyQuestion_DailyQuestionIdAndSelectedOption_OptionId(Long dailyQuestionId, Long optionId);

    /** 러빈지옥 회차 생성 시 내 빵 학습 데이터로 넘길 최근 답변. */
    List<DailyQuestionAnswer> findTop10ByMemberIdOrderByAnsweredAtDesc(Long memberId);
}
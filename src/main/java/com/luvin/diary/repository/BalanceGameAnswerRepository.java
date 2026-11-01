package com.luvin.diary.repository;

import com.luvin.diary.domain.BalanceGameAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BalanceGameAnswerRepository extends JpaRepository<BalanceGameAnswer, Long> {

    Optional<BalanceGameAnswer> findByMemberIdAndQuestionId(Long memberId, Long questionId);

    /**
     * 답을 저장한다. (member_id, daily_question_id) UNIQUE라 이미 답했으면 아무것도 바꾸지 않는다.
     * @return 저장했으면 1, 이미 답했으면 0
     */
    @Modifying
    @Query(value = "insert into daily_question_answers (member_id, daily_question_id, option_id, answered_at) "
            + "values (:memberId, :questionId, :optionId, :answeredAt) "
            + "on conflict (member_id, daily_question_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("memberId") Long memberId, @Param("questionId") Long questionId,
                       @Param("optionId") Long optionId, @Param("answeredAt") LocalDateTime answeredAt);

    /** 보기별 표 수. [optionId, count] */
    @Query("select a.optionId, count(a) from BalanceGameAnswer a where a.questionId = :questionId group by a.optionId")
    List<Object[]> countByOption(@Param("questionId") Long questionId);
}

package com.luvin.diary.repository;

import com.luvin.diary.domain.BalanceGameQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BalanceGameQuestionRepository extends JpaRepository<BalanceGameQuestion, Long> {

    Optional<BalanceGameQuestion> findByQuestionDate(LocalDate questionDate);

    /**
     * 그날 질문 자리를 잡는다. question_date UNIQUE라 동시에 요청해도 1행만 생긴다.
     * 다른 트랜잭션이 같은 날을 먼저 넣고 있으면 그 커밋을 기다렸다가 0을 돌려준다.
     * @return 새로 넣었으면 1, 이미 있으면 0
     */
    @Modifying
    @Query(value = "insert into daily_questions (content, question_date, source) "
            + "values (:content, :date, 'PRESET') on conflict (question_date) do nothing", nativeQuery = true)
    int insertForDate(@Param("content") String content, @Param("date") LocalDate date);

    /** 최근에 나간 질문 문장(중복 방지용). */
    @Query(value = "select content from daily_questions where question_date is not null and question_date < :date "
            + "order by question_date desc limit :limit", nativeQuery = true)
    List<String> findRecentContents(@Param("date") LocalDate date, @Param("limit") int limit);
}

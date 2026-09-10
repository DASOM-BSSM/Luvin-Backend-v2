package test.luvin_backend_v2.dailyquestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DailyQuestionAnswerRepository extends JpaRepository<DailyQuestionAnswer, Long> {
    boolean existsByMemberIdAndDailyQuestion_DailyQuestionId(Long memberId, Long dailyQuestionId);
    List<DailyQuestionAnswer> findAllByMemberIdOrderByAnsweredAtDesc(Long memberId);
    Optional<DailyQuestionAnswer> findTopByMemberIdOrderByDailyQuestion_DailyQuestionIdDesc(Long memberId);
    long countByMemberId(Long memberId);

    Optional<DailyQuestionAnswer> findByMemberIdAndDailyQuestion_DailyQuestionId(Long memberId, Long dailyQuestionId);
    long countByDailyQuestion_DailyQuestionId(Long dailyQuestionId);
    long countByDailyQuestion_DailyQuestionIdAndSelectedOption_OptionId(Long dailyQuestionId, Long optionId);
}
package com.luvin.dailyquestion.service;

import com.luvin.common.exception.DailyQuestionNotFoundException;
import com.luvin.common.exception.DailyQuestionOptionNotFoundException;
import com.luvin.common.exception.DuplicateAnswerException;
import com.luvin.dailyquestion.domain.DailyQuestion;
import com.luvin.dailyquestion.domain.DailyQuestionAnswer;
import com.luvin.dailyquestion.domain.DailyQuestionOption;
import com.luvin.dailyquestion.dto.DailyQuestionHistoryResponse;
import com.luvin.dailyquestion.dto.DailyQuestionOptionResponse;
import com.luvin.dailyquestion.dto.DailyQuestionOptionResultResponse;
import com.luvin.dailyquestion.dto.DailyQuestionTodayResponse;
import com.luvin.dailyquestion.repository.DailyQuestionAnswerRepository;
import com.luvin.dailyquestion.repository.DailyQuestionOptionRepository;
import com.luvin.dailyquestion.repository.DailyQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DailyQuestionServiceImpl implements DailyQuestionService {

    private final DailyQuestionRepository dailyQuestionRepository;
    private final DailyQuestionOptionRepository dailyQuestionOptionRepository;
    private final DailyQuestionAnswerRepository dailyQuestionAnswerRepository;

    public DailyQuestionServiceImpl(DailyQuestionRepository dailyQuestionRepository,
                                    DailyQuestionOptionRepository dailyQuestionOptionRepository,
                                    DailyQuestionAnswerRepository dailyQuestionAnswerRepository) {
        this.dailyQuestionRepository = dailyQuestionRepository;
        this.dailyQuestionOptionRepository = dailyQuestionOptionRepository;
        this.dailyQuestionAnswerRepository = dailyQuestionAnswerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DailyQuestionTodayResponse getTodayQuestion(Long memberId) {
        DailyQuestion today = resolveTodayQuestion(memberId);

        List<DailyQuestionOptionResponse> options = today.getOptions().stream()
                .map(option -> new DailyQuestionOptionResponse(option.getOptionId(), option.getContent()))
                .collect(Collectors.toList());

        return dailyQuestionAnswerRepository
                .findByMemberIdAndDailyQuestion_DailyQuestionId(memberId, today.getDailyQuestionId())
                .map(answer -> buildAnsweredResponse(today, options, answer))
                .orElseGet(() -> DailyQuestionTodayResponse.ofUnanswered(today, options));
    }

    private DailyQuestionTodayResponse buildAnsweredResponse(DailyQuestion today,
                                                             List<DailyQuestionOptionResponse> options,
                                                             DailyQuestionAnswer answer) {
        long totalCount = dailyQuestionAnswerRepository
                .countByDailyQuestion_DailyQuestionId(today.getDailyQuestionId());

        List<DailyQuestionOptionResultResponse> results = today.getOptions().stream()
                .map(option -> {
                    long voteCount = dailyQuestionAnswerRepository
                            .countByDailyQuestion_DailyQuestionIdAndSelectedOption_OptionId(
                                    today.getDailyQuestionId(), option.getOptionId());
                    double percentage = totalCount == 0 ? 0.0 : (voteCount * 100.0 / totalCount);
                    return new DailyQuestionOptionResultResponse(
                            option.getOptionId(), option.getContent(), voteCount, percentage);
                })
                .collect(Collectors.toList());

        return DailyQuestionTodayResponse.ofAnswered(
                today, options, answer.getSelectedOption().getOptionId(), results, totalCount);
    }

    @Override
    @Transactional
    public void submitAnswer(Long memberId, Long questionId, Long selectedOptionId) {
        DailyQuestion question = dailyQuestionRepository.findById(questionId)
                .orElseThrow(() -> new DailyQuestionNotFoundException(questionId));

        DailyQuestionOption option = dailyQuestionOptionRepository.findById(selectedOptionId)
                .orElseThrow(() -> new DailyQuestionOptionNotFoundException(selectedOptionId));

        if (!option.getDailyQuestion().getDailyQuestionId().equals(question.getDailyQuestionId())) {
            throw new DailyQuestionOptionNotFoundException(selectedOptionId);
        }

        if (dailyQuestionAnswerRepository.existsByMemberIdAndDailyQuestion_DailyQuestionId(memberId, questionId)) {
            throw new DuplicateAnswerException(questionId);
        }

        dailyQuestionAnswerRepository.save(new DailyQuestionAnswer(memberId, question, option));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyQuestionHistoryResponse> getHistory(Long memberId) {
        return dailyQuestionAnswerRepository.findAllByMemberIdOrderByAnsweredAtDesc(memberId).stream()
                .map(DailyQuestionHistoryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 사용자별 "오늘의 질문" 결정 로직.
     * - 답한 적 없으면: 첫 번째 질문(id 오름차순)
     * - 마지막으로 답한 날짜가 오늘이면: 그 질문 그대로 유지 (아직 다음 질문 안 풀림)
     * - 마지막으로 답한 날짜가 오늘 이전이면: 다음 질문으로 진행
     * - 더 이상 다음 질문이 없으면: 404 (질문 소진)
     */
    private DailyQuestion resolveTodayQuestion(Long memberId) {
        return dailyQuestionAnswerRepository
                .findTopByMemberIdOrderByDailyQuestion_DailyQuestionIdDesc(memberId)
                .map(lastAnswer -> {
                    LocalDate lastAnsweredDate = lastAnswer.getAnsweredAt().toLocalDate();
                    Long lastQuestionId = lastAnswer.getDailyQuestion().getDailyQuestionId();

                    if (lastAnsweredDate.isEqual(LocalDate.now())) {
                        return lastAnswer.getDailyQuestion();
                    }

                    return dailyQuestionRepository
                            .findFirstByDailyQuestionIdGreaterThanOrderByDailyQuestionIdAsc(lastQuestionId)
                            .orElseThrow(() -> new DailyQuestionNotFoundException(null));
                })
                .orElseGet(() -> dailyQuestionRepository.findFirstByOrderByDailyQuestionIdAsc()
                        .orElseThrow(() -> new DailyQuestionNotFoundException(null)));
    }
}

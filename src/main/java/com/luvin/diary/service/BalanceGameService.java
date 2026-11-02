package com.luvin.diary.service;

import com.luvin.common.exception.CodedApiException;
import com.luvin.diary.domain.BalanceGameOption;
import com.luvin.diary.domain.BalanceGameQuestion;
import com.luvin.diary.dto.BalanceGameDto;
import com.luvin.diary.repository.BalanceGameAnswerRepository;
import com.luvin.diary.repository.BalanceGameOptionRepository;
import com.luvin.diary.repository.BalanceGameQuestionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 오늘의 밸런스 게임(감정일기 탭 홈). 모든 사용자가 같은 날(KST 자정 기준) 같은 질문에 보기 2개 중 하나를 고른다.
 * 그룹 질문(RoomQuestionService)과 달리 그룹과 상관없다. 질문은 BalanceGamePool에서 날짜로 골라, 그날 처음 조회될 때 만든다.
 * 한 번 고르면 바꿀 수 없고, 결과(results)는 고른 뒤에만 내려준다.
 */
@Service
public class BalanceGameService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static final String DAILY_QUESTION_NOT_FOUND = "DAILY_QUESTION_NOT_FOUND";
    public static final String DAILY_QUESTION_ALREADY_ANSWERED = "DAILY_QUESTION_ALREADY_ANSWERED";
    public static final String DAILY_QUESTION_INVALID_OPTION = "DAILY_QUESTION_INVALID_OPTION";

    /** 이 기간 안에 나간 질문은 다시 고르지 않는다. */
    static final int RECENT_QUESTION_LIMIT = 20;

    private final BalanceGameQuestionRepository questionRepository;
    private final BalanceGameOptionRepository optionRepository;
    private final BalanceGameAnswerRepository answerRepository;
    private final TransactionTemplate transactionTemplate;

    public BalanceGameService(BalanceGameQuestionRepository questionRepository,
                              BalanceGameOptionRepository optionRepository,
                              BalanceGameAnswerRepository answerRepository,
                              TransactionTemplate transactionTemplate) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.answerRepository = answerRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /** 오늘 질문. 아직 없으면 만든다. 만들 수 없으면(목록이 비어 있으면) null. */
    public BalanceGameDto.View getToday(Long memberId) {
        LocalDate today = LocalDate.now(KST);
        Optional<BalanceGameQuestion> existing = transactionTemplate.execute(status ->
                questionRepository.findByQuestionDate(today));
        if (existing == null || existing.isEmpty()) {
            createFor(today);
        }
        return transactionTemplate.execute(status -> questionRepository.findByQuestionDate(today)
                .map(question -> toView(question, memberId))
                .orElse(null));
    }

    /**
     * 보기를 고른다. 한 사람당 질문 하나에 1번이고 바꿀 수 없다. 응답은 결과가 채워진 질문.
     * 404 DAILY_QUESTION_NOT_FOUND / 400 DAILY_QUESTION_INVALID_OPTION / 409 DAILY_QUESTION_ALREADY_ANSWERED
     */
    public BalanceGameDto.View answer(Long memberId, Long questionId, Long optionId) {
        return transactionTemplate.execute(status -> {
            BalanceGameQuestion question = questionRepository.findById(questionId)
                    .filter(q -> q.getQuestionDate() != null)
                    .orElseThrow(() -> new CodedApiException(HttpStatus.NOT_FOUND, DAILY_QUESTION_NOT_FOUND,
                            "밸런스 게임 질문을 찾을 수 없습니다."));
            boolean ownOption = optionId != null && question.getOptions().stream()
                    .anyMatch(option -> option.getId().equals(optionId));
            if (!ownOption) {
                throw new CodedApiException(HttpStatus.BAD_REQUEST, DAILY_QUESTION_INVALID_OPTION,
                        "이 질문의 보기가 아닙니다.");
            }
            int inserted = answerRepository.insertIfAbsent(memberId, questionId, optionId, LocalDateTime.now(KST));
            if (inserted == 0) {
                throw new CodedApiException(HttpStatus.CONFLICT, DAILY_QUESTION_ALREADY_ANSWERED,
                        "이미 답한 질문입니다.");
            }
            return toView(question, memberId);
        });
    }

    /** 그날 질문을 만든다. 동시에 여러 요청이 와도 insert가 하루 1행만 남긴다(진 쪽은 아무것도 하지 않는다). */
    private void createFor(LocalDate date) {
        if (BalanceGamePool.ENTRIES.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            List<String> recent = questionRepository.findRecentContents(date, RECENT_QUESTION_LIMIT);
            BalanceGamePool.Entry entry = BalanceGamePool.pick(date, recent);
            if (entry == null || questionRepository.insertForDate(entry.question().strip(), date) == 0) {
                return;
            }
            BalanceGameQuestion question = questionRepository.findByQuestionDate(date).orElseThrow();
            // 찬성 보기 → 반대 보기 순서로 저장한다(응답의 options·results도 보기 id 순서다).
            optionRepository.save(new BalanceGameOption(question, entry.optionA().strip()));
            optionRepository.save(new BalanceGameOption(question, entry.optionB().strip()));
        });
    }

    private BalanceGameDto.View toView(BalanceGameQuestion question, Long memberId) {
        List<BalanceGameOption> options = question.getOptions();
        List<BalanceGameDto.OptionItem> optionItems = options.stream()
                .map(option -> new BalanceGameDto.OptionItem(option.getId(), option.getContent()))
                .toList();

        Long selectedOptionId = answerRepository.findByMemberIdAndQuestionId(memberId, question.getId())
                .map(answer -> answer.getOptionId())
                .orElse(null);
        if (selectedOptionId == null) {
            return new BalanceGameDto.View(question.getId(), question.getContent(), question.getQuestionDate(),
                    optionItems, false, null, List.of(), 0);
        }

        Map<Long, Long> votes = new HashMap<>();
        for (Object[] row : answerRepository.countByOption(question.getId())) {
            votes.put((Long) row[0], (Long) row[1]);
        }
        long total = votes.values().stream().mapToLong(Long::longValue).sum();
        List<BalanceGameDto.ResultItem> results = options.stream()
                .map(option -> {
                    long count = votes.getOrDefault(option.getId(), 0L);
                    return new BalanceGameDto.ResultItem(option.getId(), option.getContent(), count,
                            percentage(count, total));
                })
                .toList();
        return new BalanceGameDto.View(question.getId(), question.getContent(), question.getQuestionDate(),
                optionItems, true, selectedOptionId, results, total);
    }

    static double percentage(long count, long total) {
        return total == 0 ? 0.0 : Math.round(count * 1000.0 / total) / 10.0;
    }
}

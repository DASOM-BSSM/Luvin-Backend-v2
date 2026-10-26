package com.luvin.learning.service;

import com.luvin.ai.client.emodi.EmodiTypes;
import com.luvin.dailyquestion.domain.DailyQuestionAnswer;
import com.luvin.dailyquestion.repository.DailyQuestionAnswerRepository;
import com.luvin.diary.domain.Diary;
import com.luvin.diary.domain.DiaryComment;
import com.luvin.diary.domain.RoomQuestion;
import com.luvin.diary.domain.RoomQuestionAnswer;
import com.luvin.diary.repository.DiaryCommentRepository;
import com.luvin.diary.repository.DiaryRepository;
import com.luvin.diary.repository.RoomQuestionAnswerRepository;
import com.luvin.diary.repository.RoomQuestionRepository;
import com.luvin.learning.domain.LearningSourceType;
import com.luvin.learning.domain.UserLearningSignal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 학습 신호가 가리키는 원문을 읽는다. 원본의 작성자가 신호의 사용자와 같을 때만 읽는다(다른 사람 글은 절대 섞지 않는다).
 * 지워진 원본은 건너뛴다. 호출부의 트랜잭션 안에서 쓴다(밸런스 답의 질문·선택지는 lazy).
 */
@Component
@RequiredArgsConstructor
public class LearningSourceReader {

    private final DiaryRepository diaryRepository;
    private final DiaryCommentRepository diaryCommentRepository;
    private final RoomQuestionAnswerRepository roomAnswerRepository;
    private final RoomQuestionRepository roomQuestionRepository;
    private final DailyQuestionAnswerRepository dailyAnswerRepository;

    /** 말투 추출 입력: 신호 순서(최근 순) 그대로의 내 글. */
    public List<EmodiTypes.WrittenText> writtenTexts(Long userId, List<UserLearningSignal> signals) {
        Map<Long, Diary> diaries = byId(diaryRepository.findAllById(ids(signals, LearningSourceType.DIARY)), Diary::getId);
        Map<Long, DiaryComment> comments = byId(diaryCommentRepository.findAllById(
                ids(signals, LearningSourceType.COMMENT)), DiaryComment::getId);
        Map<Long, RoomQuestionAnswer> answers = byId(roomAnswerRepository.findAllById(
                ids(signals, LearningSourceType.ROOM_ANSWER)), RoomQuestionAnswer::getId);

        List<EmodiTypes.WrittenText> texts = new ArrayList<>();
        for (UserLearningSignal signal : signals) {
            String text = switch (signal.getSourceType()) {
                case DIARY -> {
                    Diary d = diaries.get(signal.getSourceId());
                    yield d != null && d.getUserId().equals(userId) ? d.getContent() : null;
                }
                case COMMENT -> {
                    DiaryComment c = comments.get(signal.getSourceId());
                    yield c != null && c.getUserId().equals(userId) ? c.getContent() : null;
                }
                case ROOM_ANSWER -> {
                    RoomQuestionAnswer a = answers.get(signal.getSourceId());
                    yield a != null && a.getUserId().equals(userId) ? a.getContent() : null;
                }
                case BALANCE_ANSWER -> null;
            };
            if (text != null && !text.isBlank()) {
                String trimmed = text.strip();
                texts.add(new EmodiTypes.WrittenText(signal.getSourceType().name(),
                        trimmed.length() > LearningPolicy.SPEECH_TEXT_MAX_CHARS
                                ? trimmed.substring(0, LearningPolicy.SPEECH_TEXT_MAX_CHARS) : trimmed));
            }
        }
        return texts;
    }

    /** 성격 신호 추출 입력: 질문 + 내 답. */
    public List<EmodiTypes.AnsweredQuestion> answeredQuestions(Long userId, List<UserLearningSignal> signals) {
        Map<Long, DailyQuestionAnswer> balance = byId(dailyAnswerRepository.findAllById(
                ids(signals, LearningSourceType.BALANCE_ANSWER)), DailyQuestionAnswer::getAnswerId);
        Map<Long, RoomQuestionAnswer> roomAnswers = byId(roomAnswerRepository.findAllById(
                ids(signals, LearningSourceType.ROOM_ANSWER)), RoomQuestionAnswer::getId);
        Map<Long, RoomQuestion> roomQuestions = byId(roomQuestionRepository.findAllById(roomAnswers.values().stream()
                .map(RoomQuestionAnswer::getQuestionId).distinct().toList()), RoomQuestion::getId);

        List<EmodiTypes.AnsweredQuestion> answered = new ArrayList<>();
        for (UserLearningSignal signal : signals) {
            if (signal.getSourceType() == LearningSourceType.BALANCE_ANSWER) {
                DailyQuestionAnswer a = balance.get(signal.getSourceId());
                if (a != null && a.getMemberId().equals(userId)) {
                    answered.add(new EmodiTypes.AnsweredQuestion(signal.getSourceType().name(),
                            a.getDailyQuestion().getContent(), a.getSelectedOption().getContent()));
                }
            } else if (signal.getSourceType() == LearningSourceType.ROOM_ANSWER) {
                RoomQuestionAnswer a = roomAnswers.get(signal.getSourceId());
                RoomQuestion q = a == null ? null : roomQuestions.get(a.getQuestionId());
                if (q != null && q.getContent() != null && a.getUserId().equals(userId)) {
                    answered.add(new EmodiTypes.AnsweredQuestion(signal.getSourceType().name(), q.getContent(),
                            a.getContent()));
                }
            }
        }
        return answered;
    }

    private static List<Long> ids(List<UserLearningSignal> signals, LearningSourceType type) {
        return signals.stream().filter(s -> s.getSourceType() == type).map(UserLearningSignal::getSourceId).toList();
    }

    private static <T> Map<Long, T> byId(Collection<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}

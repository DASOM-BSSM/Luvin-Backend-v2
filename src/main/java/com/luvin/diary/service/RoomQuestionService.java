package com.luvin.diary.service;

import com.luvin.ai.client.emodi.AiEmodiClient;
import com.luvin.ai.client.emodi.EmodiTypes;
import com.luvin.ai.config.AiStoryProperties;
import com.luvin.ai.security.OwnerSubjectProvider;
import com.luvin.common.exception.CodedApiException;
import com.luvin.diary.domain.DiaryRoom;
import com.luvin.diary.domain.RoomQuestion;
import com.luvin.diary.domain.RoomQuestionStatus;
import com.luvin.diary.dto.RoomQuestionDto;
import com.luvin.diary.repository.DiaryRoomRepository;
import com.luvin.diary.repository.RoomQuestionAnswerRepository;
import com.luvin.diary.repository.RoomQuestionRepository;
import com.luvin.learning.domain.LearningSourceType;
import com.luvin.learning.service.LearningSignalService;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 그룹(공유방) 질문. 그룹 멤버 누구나 "질문 생성하기"로 AI 질문을 만들 수 있고, 그룹당 KST 정각 칸(예: 09:00~09:59)마다 1개다.
 * 칸은 생성 버튼을 누른 시각을 KST 정각으로 내린 것이다.
 * 생성은 비동기다: POST는 바로 status generating으로 응답하고, AI 응답이 오면 ready(실패하면 failed)로 바뀐다.
 * AI에는 그룹 이름·멤버 수·최근 질문만 넘긴다(일기 본문은 넘기지 않는다).
 */
@Service
public class RoomQuestionService {

    private static final Logger log = LoggerFactory.getLogger(RoomQuestionService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static final String ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
    public static final String ROOM_FORBIDDEN = "ROOM_FORBIDDEN";
    public static final String ROOM_QUESTION_NOT_FOUND = "ROOM_QUESTION_NOT_FOUND";
    public static final String ROOM_QUESTION_ALREADY_EXISTS = "ROOM_QUESTION_ALREADY_EXISTS";
    public static final String ROOM_QUESTION_NOT_READY = "ROOM_QUESTION_NOT_READY";
    public static final String ROOM_ANSWER_INVALID = "ROOM_ANSWER_INVALID";
    public static final String ROOM_QUESTION_RANGE_INVALID = "ROOM_QUESTION_RANGE_INVALID";

    static final int ANSWER_MAX_LENGTH = 200;
    static final int QUESTION_MAX_LENGTH = 150;
    /** AI에 넘기는 최근 질문 수(중복 방지). */
    static final int RECENT_QUESTION_LIMIT = 20;
    static final int MAX_PAGE_SIZE = 50;

    private final RoomQuestionRepository questionRepository;
    private final RoomQuestionAnswerRepository answerRepository;
    private final DiaryRoomRepository roomRepository;
    private final UserRepository userRepository;
    private final LearningSignalService learningSignalService;
    private final AiEmodiClient emodiClient;
    private final OwnerSubjectProvider ownerSubjectProvider;
    private final AiStoryProperties storyProperties;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executor;

    public RoomQuestionService(RoomQuestionRepository questionRepository,
                               RoomQuestionAnswerRepository answerRepository,
                               DiaryRoomRepository roomRepository,
                               UserRepository userRepository,
                               LearningSignalService learningSignalService,
                               AiEmodiClient emodiClient,
                               OwnerSubjectProvider ownerSubjectProvider,
                               AiStoryProperties storyProperties,
                               TransactionTemplate transactionTemplate,
                               @Qualifier("aiStoryExecutor") ExecutorService executor) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.learningSignalService = learningSignalService;
        this.emodiClient = emodiClient;
        this.ownerSubjectProvider = ownerSubjectProvider;
        this.storyProperties = storyProperties;
        this.transactionTemplate = transactionTemplate;
        this.executor = executor;
    }

    /**
     * 지금 칸(KST 정각)의 질문 생성을 시작한다. 이미 이 칸에 질문이 있으면(생성 중이거나 준비됨) 409 ROOM_QUESTION_ALREADY_EXISTS와 함께
     * 그 질문을 data로 돌려준다. 실패했거나 오래 멈춘 생성은 다시 시작한다.
     */
    public RoomQuestionDto.QuestionView create(Long memberId, Long roomId) {
        DiaryRoom room = requireMember(memberId, roomId);
        ZonedDateTime slot = ZonedDateTime.now(KST).truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime slotStartAt = slot.toOffsetDateTime();

        Long questionId = transactionTemplate.execute(status -> {
            boolean claimed = questionRepository.claimSlot(roomId, slotStartAt, slot.toLocalDate(), memberId) == 1;
            RoomQuestion question = questionRepository.findByRoomIdAndSlotStartAt(roomId, slotStartAt).orElseThrow();
            if (!claimed && questionRepository.reclaim(question.getId(), memberId, staleBefore()) == 0) {
                throw new CodedApiException(HttpStatus.CONFLICT, ROOM_QUESTION_ALREADY_EXISTS,
                        "이 시간대 질문이 이미 있습니다.", view(question, memberId));
            }
            return question.getId();
        });

        String ownerSubject = ownerSubjectProvider.resolve(memberId);
        String roomName = room.getName();
        executor.execute(() -> generate(questionId, roomId, roomName, ownerSubject));
        return transactionTemplate.execute(status ->
                view(questionRepository.findById(questionId).orElseThrow(), memberId));
    }

    /** 지금 질문(가장 최근 칸). 없으면 null. */
    public RoomQuestionDto.QuestionView getCurrent(Long memberId, Long roomId) {
        requireMember(memberId, roomId);
        return transactionTemplate.execute(status -> questionRepository.findFirstByRoomIdOrderBySlotStartAtDesc(roomId)
                .map(question -> view(question, memberId))
                .orElse(null));
    }

    /** 질문 하나(지난 질문 포함). 응답은 GET current와 같은 형태. */
    public RoomQuestionDto.QuestionView get(Long memberId, Long roomId, Long questionId) {
        requireMember(memberId, roomId);
        return transactionTemplate.execute(status -> view(questionInRoom(roomId, questionId), memberId));
    }

    /** 답하기. 한 사람당 1개이고 다시 보내면 내용을 바꾼다. 응답은 답이 반영된 질문. */
    public RoomQuestionDto.QuestionView answer(Long memberId, Long roomId, Long questionId, String rawContent) {
        requireMember(memberId, roomId);
        String content = rawContent == null ? "" : rawContent.strip();
        if (content.isEmpty() || content.length() > ANSWER_MAX_LENGTH) {
            throw new CodedApiException(HttpStatus.BAD_REQUEST, ROOM_ANSWER_INVALID, "답은 1~200자로 입력해주세요.");
        }
        return transactionTemplate.execute(status -> {
            RoomQuestion question = questionInRoom(roomId, questionId);
            if (statusOf(question) != RoomQuestionStatus.READY) {
                throw new CodedApiException(HttpStatus.CONFLICT, ROOM_QUESTION_NOT_READY, "아직 질문이 준비되지 않았습니다.");
            }
            answerRepository.upsert(questionId, memberId, content);
            answerRepository.findByQuestionIdAndUserId(questionId, memberId).ifPresent(answer ->
                    learningSignalService.record(memberId, LearningSourceType.ROOM_ANSWER, answer.getId()));
            return view(question, memberId);
        });
    }

    /** 내 답 삭제. 학습 신호도 함께 지운다. 답이 없어도 200. */
    public RoomQuestionDto.QuestionView deleteMyAnswer(Long memberId, Long roomId, Long questionId) {
        requireMember(memberId, roomId);
        return transactionTemplate.execute(status -> {
            RoomQuestion question = questionInRoom(roomId, questionId);
            answerRepository.findByQuestionIdAndUserId(questionId, memberId).ifPresent(answer -> {
                learningSignalService.remove(LearningSourceType.ROOM_ANSWER, answer.getId());
                answerRepository.deleteByQuestionIdAndUserId(questionId, memberId);
            });
            return view(question, memberId);
        });
    }

    /**
     * 지난 질문 목록(최신순). from·to(KST 날짜 yyyy-MM-dd, 둘 다 포함)를 주면 그 날짜 범위의 칸만 준다.
     * 한쪽만 주면 다른 쪽은 끝까지. 형식이 틀렸거나 from이 to보다 뒤면 400 ROOM_QUESTION_RANGE_INVALID.
     */
    public List<RoomQuestionDto.HistoryItem> getHistory(Long memberId, Long roomId, String rawFrom, String rawTo,
                                                        int page, int size) {
        requireMember(memberId, roomId);
        LocalDate from = parseDate(rawFrom);
        LocalDate to = parseDate(rawTo);
        if (from != null && to != null && from.isAfter(to)) {
            throw new CodedApiException(HttpStatus.BAD_REQUEST, ROOM_QUESTION_RANGE_INVALID,
                    "from은 to보다 늦을 수 없습니다.");
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);
        return transactionTemplate.execute(status -> {
            List<RoomQuestion> questions = from == null && to == null
                    ? questionRepository.findByRoomIdOrderBySlotStartAtDesc(roomId, pageable)
                    : questionRepository.findInSlotRange(roomId,
                            startOfDay(from == null ? LocalDate.EPOCH : from),
                            startOfDay((to == null ? LocalDate.now(KST) : to).plusDays(1)),
                            pageable);
            Map<Long, Long> counts = questions.isEmpty() ? Map.of()
                    : answerRepository.countByQuestionIds(questions.stream().map(RoomQuestion::getId).toList())
                    .stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
            return questions.stream()
                    .map(q -> {
                        String wire = wireStatus(q);
                        return new RoomQuestionDto.HistoryItem(q.getId(), q.getQuestionDate(), q.getSlotStartAt(),
                                "ready".equals(wire) ? q.getContent() : null, wire, q.getCreatedAt(),
                                counts.getOrDefault(q.getId(), 0L));
                    })
                    .toList();
        });
    }

    // ---------------------------------------------------------------- 생성(백그라운드)

    void generate(Long questionId, Long roomId, String roomName, String ownerSubject) {
        try {
            List<String> recent = questionRepository.findRecentContents(roomId, PageRequest.of(0, RECENT_QUESTION_LIMIT));
            int memberCount = (int) roomRepository.countMembers(roomId);
            EmodiTypes.QuestionResponse response = emodiClient.roomQuestion(ownerSubject,
                    new EmodiTypes.RoomQuestionRequest(roomName, memberCount, recent));
            String content = response == null || response.content() == null ? "" : response.content().strip();
            if (content.isEmpty() || content.length() > QUESTION_MAX_LENGTH) {
                throw new IllegalStateException("AI 그룹 질문이 비었거나 너무 깁니다. length=" + content.length());
            }
            transactionTemplate.executeWithoutResult(status -> questionRepository.markReady(questionId, content));
        } catch (RuntimeException e) {
            log.warn("room question generation failed: questionId={}, cause={}", questionId, e.getMessage());
            transactionTemplate.executeWithoutResult(status -> questionRepository.markFailed(questionId));
        }
    }

    // ---------------------------------------------------------------- 내부 헬퍼

    private DiaryRoom requireMember(Long memberId, Long roomId) {
        DiaryRoom room = roomRepository.findById(roomId).orElseThrow(() ->
                new CodedApiException(HttpStatus.NOT_FOUND, ROOM_NOT_FOUND, "공유방을 찾을 수 없습니다."));
        if (!room.isOwnedBy(memberId) && !roomRepository.isMember(roomId, memberId)) {
            throw new CodedApiException(HttpStatus.FORBIDDEN, ROOM_FORBIDDEN, "공유방 멤버만 이용할 수 있습니다.");
        }
        return room;
    }

    private RoomQuestion questionInRoom(Long roomId, Long questionId) {
        return questionRepository.findById(questionId)
                .filter(q -> q.getRoomId().equals(roomId))
                .orElseThrow(() -> new CodedApiException(HttpStatus.NOT_FOUND, ROOM_QUESTION_NOT_FOUND,
                        "질문을 찾을 수 없습니다."));
    }

    private RoomQuestionDto.QuestionView view(RoomQuestion question, Long memberId) {
        String status = wireStatus(question);
        User creator = userRepository.findById(question.getRequestedBy()).orElse(null);
        if (!"ready".equals(status)) {
            return RoomQuestionDto.QuestionView.of(question, status, creator, null, List.of());
        }
        List<RoomQuestionDto.AnswerItem> answers = answerRepository.findAnswerItems(question.getId());
        RoomQuestionDto.AnswerItem mine = answers.stream()
                .filter(a -> a.authorId().equals(memberId))
                .findFirst().orElse(null);
        return RoomQuestionDto.QuestionView.of(question, status, creator, mine, answers);
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.strip());
        } catch (DateTimeParseException e) {
            throw new CodedApiException(HttpStatus.BAD_REQUEST, ROOM_QUESTION_RANGE_INVALID,
                    "날짜는 yyyy-MM-dd 형식으로 입력해주세요.");
        }
    }

    private static OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay(KST).toOffsetDateTime();
    }

    /** 서버 재시작 등으로 오래 멈춘 GENERATING은 failed로 보여준다(POST로 다시 생성할 수 있다). */
    private RoomQuestionStatus statusOf(RoomQuestion question) {
        if (question.getStatus() == RoomQuestionStatus.GENERATING && question.getUpdatedAt().isBefore(staleBefore())) {
            return RoomQuestionStatus.FAILED;
        }
        return question.getStatus();
    }

    private String wireStatus(RoomQuestion question) {
        return statusOf(question).name().toLowerCase();
    }

    private OffsetDateTime staleBefore() {
        return OffsetDateTime.now().minus(storyProperties.generationStaleAfter());
    }
}

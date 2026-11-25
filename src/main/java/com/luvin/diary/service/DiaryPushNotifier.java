package com.luvin.diary.service;

import com.luvin.diary.domain.DiaryRoom;
import com.luvin.diary.domain.DiaryRoomMember;
import com.luvin.diary.repository.DiaryRoomRepository;
import com.luvin.push.client.PushClient;
import com.luvin.push.client.PushMessage;
import com.luvin.push.service.PushTokenService;
import com.luvin.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * 감정일기 푸시 3종(앱과 맞춘 계약 — type 이름과 data 형식을 바꾸면 안 된다).
 * <ul>
 *   <li>QUESTION_CREATED: 그룹원이 만든 질문이 준비됨 → 같은 그룹의 다른 멤버 전원. data {type, roomId}</li>
 *   <li>QUESTION_ANSWERED: 그룹원이 질문에 처음 답함 → 같은 그룹의 다른 멤버 전원. data {type, roomId}</li>
 *   <li>DIARY_COMMENT: 내 일기에 댓글 → 그 일기 작성자. data {type, roomId, diaryId}</li>
 * </ul>
 * 본인이 한 행동은 본인에게 보내지 않는다. 이름은 그룹 닉네임, 없으면 계정 닉네임(없으면 이름)을 쓴다.
 * Android 채널은 앱이 만들어 둔 {@value #CHANNEL_ID}. roomId·diaryId는 숫자다.
 *
 * <p>보내는 시점은 그 행동을 저장한 트랜잭션이 커밋된 뒤이고, 요청 스레드를 붙잡지 않게 따로 보낸다. 실패는 로그만 남기고
 * 원래 동작에는 영향이 없다. 발송 시간 제한(KST 06:00~23:59)은 PushClient 앞(QuietHoursPushClient)에서 한 번에 거른다.
 */
@Service
public class DiaryPushNotifier {

    private static final Logger log = LoggerFactory.getLogger(DiaryPushNotifier.class);

    public static final String QUESTION_CREATED = "QUESTION_CREATED";
    public static final String QUESTION_ANSWERED = "QUESTION_ANSWERED";
    public static final String DIARY_COMMENT = "DIARY_COMMENT";
    public static final String CHANNEL_ID = "diary-activity";
    static final String DEFAULT_TITLE = "감정일기";

    private final DiaryRoomRepository roomRepository;
    private final UserRepository userRepository;
    private final PushTokenService pushTokenService;
    private final PushClient pushClient;
    private final ExecutorService executor;

    public DiaryPushNotifier(DiaryRoomRepository roomRepository, UserRepository userRepository,
                             PushTokenService pushTokenService, PushClient pushClient,
                             @Qualifier("aiStoryExecutor") ExecutorService executor) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.pushTokenService = pushTokenService;
        this.pushClient = pushClient;
        this.executor = executor;
    }

    /** 그룹원(creatorId)이 만든 질문이 준비됐다. */
    public void questionCreated(Long roomId, Long creatorId) {
        afterCommit(QUESTION_CREATED, () -> {
            List<DiaryRoomMember> members = roomRepository.findMembersByRoomId(roomId);
            sendToRoom(roomId, members, creatorId, QUESTION_CREATED,
                    nameOf(members, creatorId) + "님이 질문을 만들었어요", Map.of());
        });
    }

    /** 그룹원(answererId)이 질문에 답을 올렸다(처음 올릴 때만 부른다 — 수정은 알리지 않는다). */
    public void questionAnswered(Long roomId, Long answererId) {
        afterCommit(QUESTION_ANSWERED, () -> {
            List<DiaryRoomMember> members = roomRepository.findMembersByRoomId(roomId);
            sendToRoom(roomId, members, answererId, QUESTION_ANSWERED,
                    nameOf(members, answererId) + "님이 답변을 올렸어요", Map.of());
        });
    }

    /** commenterId가 authorId의 일기(diaryId)에 댓글을 남겼다. 자기 일기에 단 댓글은 알리지 않는다. */
    public void diaryCommented(Long roomId, Long diaryId, Long authorId, Long commenterId) {
        if (Objects.equals(authorId, commenterId)) {
            return;
        }
        afterCommit(DIARY_COMMENT, () -> {
            List<DiaryRoomMember> members = roomRepository.findMembersByRoomId(roomId);
            if (members.stream().noneMatch(m -> userIdOf(m).equals(authorId))) {
                return; // 작성자가 그룹을 나갔으면 일기를 볼 수 없으니 보내지 않는다.
            }
            send(List.of(authorId), titleOf(roomId), nameOf(members, commenterId) + "님이 내 기분에 댓글을 남겼어요",
                    data(DIARY_COMMENT, roomId, Map.of("diaryId", diaryId)));
        });
    }

    private void sendToRoom(Long roomId, List<DiaryRoomMember> members, Long actorId, String type, String body,
                            Map<String, Object> extra) {
        List<Long> recipients = members.stream().map(DiaryPushNotifier::userIdOf)
                .filter(id -> !id.equals(actorId))
                .toList();
        send(recipients, titleOf(roomId), body, data(type, roomId, extra));
    }

    private void send(Collection<Long> recipients, String title, String body, Map<String, Object> data) {
        List<String> tokens = pushTokenService.tokensOf(recipients);
        if (tokens.isEmpty()) {
            return;
        }
        List<PushMessage> messages = tokens.stream()
                .map(token -> new PushMessage(token, title, body, data, CHANNEL_ID))
                .toList();
        pushTokenService.removeTokens(pushClient.send(messages));
    }

    private static Map<String, Object> data(String type, Long roomId, Map<String, Object> extra) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("roomId", roomId);
        data.putAll(extra);
        return data;
    }

    /** 알림 제목은 그룹 이름(없으면 "감정일기"). */
    private String titleOf(Long roomId) {
        return roomRepository.findById(roomId).map(DiaryRoom::getName)
                .filter(name -> !name.isBlank())
                .orElse(DEFAULT_TITLE);
    }

    /** 그룹 닉네임 → 계정 닉네임 → 이름. */
    private String nameOf(List<DiaryRoomMember> members, Long userId) {
        Optional<String> roomNickname = members.stream()
                .filter(m -> userIdOf(m).equals(userId))
                .map(DiaryRoomMember::getNickname)
                .filter(n -> n != null && !n.isBlank())
                .findFirst();
        if (roomNickname.isPresent()) {
            return roomNickname.get();
        }
        return userRepository.findById(userId)
                .map(user -> firstNonBlank(user.getNickname(), user.getName()))
                .orElse("누군가");
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : "누군가";
    }

    private static Long userIdOf(DiaryRoomMember member) {
        return member.getId().getUserId();
    }

    /** 지금 트랜잭션이 있으면 커밋된 뒤에, 없으면 바로 따로 보낸다. 롤백되면 보내지 않는다. */
    private void afterCommit(String type, Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("diary push failed: type={}, cause={}", type, e.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(safe);
                }
            });
        } else {
            executor.execute(safe);
        }
    }
}

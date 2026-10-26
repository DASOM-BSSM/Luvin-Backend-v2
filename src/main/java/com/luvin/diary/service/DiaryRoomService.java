package com.luvin.diary.service;

import com.luvin.common.exception.BusinessException;
import com.luvin.common.exception.CodedApiException;
import com.luvin.common.exception.DiaryRoomInviteCodeNotFoundException;
import com.luvin.common.exception.DiaryRoomNotFoundException;
import com.luvin.common.exception.DiaryRoomOwnerCannotLeaveException;
import com.luvin.common.exception.ErrorCode;
import com.luvin.common.exception.UserNotFoundException;
import com.luvin.diary.domain.DiaryRoom;
import com.luvin.diary.dto.DiaryDto;
import com.luvin.diary.dto.DiaryRoomDto;
import com.luvin.diary.repository.DiaryCommentRepository;
import com.luvin.diary.repository.DiaryRepository;
import com.luvin.diary.repository.DiaryRoomRepository;
import com.luvin.diary.repository.RoomQuestionAnswerRepository;
import com.luvin.diary.repository.RoomQuestionRepository;
import com.luvin.learning.service.LearningSignalService;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiaryRoomService {
    private final DiaryRoomRepository diaryRoomRepository;
    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final DiaryCommentRepository diaryCommentRepository;
    private final RoomQuestionRepository roomQuestionRepository;
    private final RoomQuestionAnswerRepository roomQuestionAnswerRepository;
    private final LearningSignalService learningSignalService;

    /** 초대 코드 글자. 헷갈리는 0/O, 1/I/L은 뺐다. Flyway V7의 chars와 반드시 같게 유지한다. */
    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int INVITE_CODE_MAX_TRIES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private DiaryRoom getRoom(Long roomId) {
        return diaryRoomRepository.findById(roomId)
                .orElseThrow(() -> new DiaryRoomNotFoundException(roomId));
    }

    private DiaryRoom getRoomForUpdate(Long roomId) {
        return diaryRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new DiaryRoomNotFoundException(roomId));
    }

    // 공유방 생성
    @Transactional
    public DiaryRoomDto.Response createRoom(Long memberId, DiaryRoomDto.CreateRequest requestDto) {
        String ownerNickname = requestDto.ownerNickname().strip();
        DiaryRoom room = new DiaryRoom(requestDto.name(), requestDto.description(), memberId, requestDto.maxMemberCount());
        room.changeInviteCode(newUniqueInviteCode());
        DiaryRoom savedRoom = diaryRoomRepository.save(room);

        // 방 생성을 요청한 유저를 해당 방의 방장(OWNER)으로 자동 등록
        diaryRoomRepository.insertOwner(savedRoom.getId(), memberId, ownerNickname);

        return new DiaryRoomDto.Response(savedRoom, 1);
    }

    // 공유방 조회
    @Transactional(readOnly = true)
    public List<DiaryRoomDto.Response> getMyRooms(Long memberId) {
        List<DiaryRoom> rooms = diaryRoomRepository.findAllByMemberId(memberId);
        if (rooms.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = diaryRoomRepository.countMembersByRoomIds(rooms.stream().map(DiaryRoom::getId).toList())
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return rooms.stream()
                .map(room -> new DiaryRoomDto.Response(room, counts.getOrDefault(room.getId(), 0L)))
                .toList();
    }

    // 공유방 단건 조회
    @Transactional(readOnly = true)
    public DiaryRoomDto.Response getRoomDetail(Long memberId, Long roomId) {
        DiaryRoom room = getRoom(roomId);
        if (!room.isOwnedBy(memberId) && !diaryRoomRepository.isMember(roomId, memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return new DiaryRoomDto.Response(room, diaryRoomRepository.countMembers(roomId));
    }

    // 공유방 수정
    @Transactional
    public DiaryRoomDto.Response updateRoom(Long memberId, Long roomId, DiaryRoomDto.UpdateRequest requestDto) {
        DiaryRoom room = getRoomForUpdate(roomId);

        // 방장 권한 체크
        if (!room.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        long memberCount = diaryRoomRepository.countMembers(roomId);
        if (requestDto.maxMemberCount() < memberCount) {
            throw new CodedApiException(HttpStatus.CONFLICT, "DIARY_ROOM_MAX_BELOW_MEMBER_COUNT",
                    "최대 인원은 현재 인원(" + memberCount + "명)보다 적게 정할 수 없습니다.");
        }
        String ownerNickname = requestDto.ownerNickname().strip();
        checkNicknameAvailable(roomId, ownerNickname, memberId);

        room.update(requestDto.name(), requestDto.description(), requestDto.maxMemberCount());
        // 방 변경을 먼저 flush하고 닉네임을 바꾼다 (updateNickname이 영속성 컨텍스트를 비운다).
        diaryRoomRepository.updateNickname(roomId, memberId, ownerNickname);
        return new DiaryRoomDto.Response(room, memberCount);
    }

    // 공유방 삭제
    @Transactional
    public void deleteRoom(Long memberId, Long roomId) {
        DiaryRoom room = getRoom(roomId);

        // 방장 권한 체크
        if (!room.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        deleteRoomWithContents(roomId);
    }

    /**
     * 회원 탈퇴 시 users 행을 지우기 전에 호출한다. diary_room_member.user_id가 users FK라 멤버 행이
     * 남아 있으면 탈퇴가 FK 오류로 실패한다. 방장인 방은 관리할 사람이 없어지므로 방째로 지우고,
     * 나머지 방에서는 나가기만 한다(그 방에 쓴 일기·댓글은 user_id가 FK 없는 컬럼이라 남겨 둔다).
     */
    @Transactional
    public void removeUserFromAllRooms(Long userId) {
        for (Long roomId : diaryRoomRepository.findIdsByOwnerId(userId)) {
            deleteRoomWithContents(roomId);
        }
        diaryRoomRepository.deleteAllMembershipsByUserId(userId);
    }

    private void deleteRoomWithContents(Long roomId) {
        // DB FK에 ON DELETE 설정이 없어서, 딸린 데이터를 먼저 정리해야 FK 오류가 나지 않는다.
        // 일기는 항상 방 안에 있어야 하므로 방의 일기도 함께 지운다:
        // 학습 신호 → 그룹 질문 답 → 그룹 질문 → 반응 → 댓글 → 일기 → 멤버 → 방
        // 학습 신호는 일기·댓글·답 테이블을 조인해서 찾으므로 가장 먼저 지운다.
        learningSignalService.removeForRoom(roomId);
        roomQuestionAnswerRepository.deleteAllByRoomId(roomId);
        roomQuestionRepository.deleteAllByRoomId(roomId);
        diaryRepository.deleteReactionsByRoomId(roomId);
        diaryCommentRepository.deleteAllByRoomId(roomId);
        diaryRepository.deleteAllByRoomId(roomId);
        diaryRoomRepository.deleteAllMembersByRoomId(roomId);
        diaryRoomRepository.deleteById(roomId);
    }

    // 공유방 목록 멤버 조회
    @Transactional(readOnly = true)
    public List<DiaryRoomDto.MemberResponse> getRoomMembers(Long memberId, Long roomId) {
        DiaryRoom room = getRoom(roomId);

        // 멤버 이상 접근 가능 검증
        if (!room.isOwnedBy(memberId) && !diaryRoomRepository.isMember(roomId, memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return diaryRoomRepository.findMembersByRoomId(roomId).stream()
                .map(DiaryRoomDto.MemberResponse::new)
                .toList();
    }

    @Transactional
    public DiaryRoomDto.MembershipResponse leave(Long memberId, Long roomId) {
        DiaryRoom room = getRoom(roomId);
        if (room.isOwnedBy(memberId)) {
            throw new DiaryRoomOwnerCannotLeaveException();
        }
        diaryRoomRepository.deleteMember(roomId, memberId);
        long memberCount = diaryRoomRepository.countMembers(roomId);
        return new DiaryRoomDto.MembershipResponse(roomId, memberId, false, memberCount);
    }

    /**
     * 멤버 추가(초대). 방장만 할 수 있다. 방 닉네임은 방장이 정해 보낸 값이고, 비워 두면 계정 닉네임(없으면 이름)이다.
     * 초대 코드 참여와 같이 인원이 꽉 찼거나 닉네임이 겹치면 409.
     */
    @Transactional
    public DiaryRoomDto.MembershipResponse addMember(Long memberId, Long roomId, Long targetUserId, String rawNickname) {
        DiaryRoom room = getRoomForUpdate(roomId);
        if (!room.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // users FK가 없어서 DB가 막아주지 않으므로, 없는 사용자는 여기서 막는다.
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        // 이미 멤버면 에러 없이 진행한다 (멱등).
        if (!diaryRoomRepository.isMember(roomId, targetUserId)) {
            String nickname = rawNickname == null || rawNickname.isBlank()
                    ? DiaryRoomDto.defaultNickname(target) : rawNickname.strip();
            addMemberChecked(room, targetUserId, nickname);
        }
        long memberCount = diaryRoomRepository.countMembers(roomId);
        return new DiaryRoomDto.MembershipResponse(roomId, targetUserId, true, memberCount);
    }

    @Transactional
    public DiaryRoomDto.KickResponse kickMember(Long memberId, Long roomId, Long targetUserId) {
        DiaryRoom room = getRoom(roomId);
        if (!room.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (targetUserId.equals(memberId)) {
            throw new DiaryRoomOwnerCannotLeaveException();
        }
        // 강퇴된 사람이 같은 초대 코드로 바로 다시 들어오지 못하도록, 실제로 강퇴됐을 때만 코드를 새로 발급한다.
        // (이전 코드는 다른 사람도 더는 쓸 수 없으니, 방장·멤버는 새 코드를 조회해서 다시 공유해야 한다.)
        if (diaryRoomRepository.deleteMember(roomId, targetUserId) > 0) {
            diaryRoomRepository.updateInviteCode(roomId, newUniqueInviteCode());
        }
        return new DiaryRoomDto.KickResponse(roomId, targetUserId, "공유방에서 나갔습니다.");
    }

    @Transactional(readOnly = true)
    public List<DiaryDto.FeedItem> getRoomDiaries(Long memberId, Long roomId) {
        DiaryRoom room = getRoom(roomId);
        if (!room.isOwnedBy(memberId) && !diaryRoomRepository.isMember(roomId, memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return diaryRepository.findRoomFeed(roomId, memberId);
    }

    // ------------------------------------------------------------------ 초대 코드

    /** 초대 코드 조회. 방장·멤버만 볼 수 있다. (기존 방의 코드는 Flyway V7이 미리 채운다.) */
    @Transactional(readOnly = true)
    public DiaryRoomDto.InviteCodeResponse getInviteCode(Long memberId, Long roomId) {
        DiaryRoom room = getRoom(roomId);
        if (!room.isOwnedBy(memberId) && !diaryRoomRepository.isMember(roomId, memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return new DiaryRoomDto.InviteCodeResponse(roomId, room.getInviteCode());
    }

    /** 초대 코드 재발급 (코드가 유출됐을 때). 방장만 가능하고, 이전 코드는 바로 쓸 수 없게 된다. */
    @Transactional
    public DiaryRoomDto.InviteCodeResponse regenerateInviteCode(Long memberId, Long roomId) {
        DiaryRoom room = getRoom(roomId);
        if (!room.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        room.changeInviteCode(newUniqueInviteCode());
        return new DiaryRoomDto.InviteCodeResponse(roomId, room.getInviteCode());
    }

    /**
     * 초대 코드로 참여. 코드를 아는 사람은 최대 인원까지 들어올 수 있다. 대소문자·앞뒤 공백은 무시한다.
     * 이미 멤버면 아무것도 하지 않고 200 (멱등, 닉네임도 그대로). 꽉 찼으면 409 DIARY_ROOM_FULL,
     * 같은 방에 같은 닉네임이 있으면 409 DIARY_ROOM_NICKNAME_DUPLICATED.
     */
    @Transactional
    public DiaryRoomDto.MembershipResponse joinByInviteCode(Long memberId, DiaryRoomDto.JoinRequest request) {
        String inviteCode = request.inviteCode().strip().toUpperCase(Locale.ROOT);
        // 인원·닉네임 확인과 추가 사이에 다른 참여가 끼어들지 않도록 방 행을 잠근다.
        DiaryRoom room = diaryRoomRepository.findByInviteCodeForUpdate(inviteCode)
                .orElseThrow(DiaryRoomInviteCodeNotFoundException::new);
        Long roomId = room.getId();

        if (!diaryRoomRepository.isMember(roomId, memberId)) {
            addMemberChecked(room, memberId, request.nickname().strip());
        }
        long memberCount = diaryRoomRepository.countMembers(roomId);
        return new DiaryRoomDto.MembershipResponse(roomId, memberId, true, memberCount);
    }

    /** 잠근 방에 새 멤버를 넣는다. 꽉 찼거나 닉네임이 겹치면 409. */
    private void addMemberChecked(DiaryRoom room, Long userId, String nickname) {
        if (diaryRoomRepository.countMembers(room.getId()) >= room.getMaxMemberCount()) {
            throw new CodedApiException(HttpStatus.CONFLICT, "DIARY_ROOM_FULL",
                    "공유방 인원이 가득 찼습니다. (최대 " + room.getMaxMemberCount() + "명)");
        }
        checkNicknameAvailable(room.getId(), nickname, userId);
        diaryRoomRepository.insertMemberIfAbsent(room.getId(), userId, nickname);
    }

    private void checkNicknameAvailable(Long roomId, String nickname, Long userId) {
        if (diaryRoomRepository.existsNickname(roomId, nickname, userId)) {
            throw new CodedApiException(HttpStatus.CONFLICT, "DIARY_ROOM_NICKNAME_DUPLICATED",
                    "이 공유방에서 이미 쓰고 있는 닉네임입니다.");
        }
    }

    private String newUniqueInviteCode() {
        for (int i = 0; i < INVITE_CODE_MAX_TRIES; i++) {
            String code = randomInviteCode();
            if (!diaryRoomRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        // 31^8 ≈ 8500억 가지라 사실상 오지 않는다. 오면 DB 상태가 비정상이라 바로 알 수 있게 실패시킨다.
        throw new IllegalStateException("초대 코드 발급에 실패했습니다.");
    }

    private static String randomInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}

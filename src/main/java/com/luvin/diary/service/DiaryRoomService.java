package com.luvin.diary.service;

import com.luvin.common.exception.BusinessException;
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
import com.luvin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DiaryRoomService {
    private final DiaryRoomRepository diaryRoomRepository;
    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final DiaryCommentRepository diaryCommentRepository;

    /** 초대 코드 글자. 헷갈리는 0/O, 1/I/L은 뺐다. */
    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int INVITE_CODE_MAX_TRIES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private DiaryRoom getRoom(Long roomId) {
        return diaryRoomRepository.findById(roomId)
                .orElseThrow(() -> new DiaryRoomNotFoundException(roomId));
    }

    // 공유방 생성
    @Transactional
    public DiaryRoomDto.Response createRoom(Long memberId, DiaryRoomDto.Request requestDto) {
        DiaryRoom room = new DiaryRoom(requestDto.name(), requestDto.description(), memberId);
        room.changeInviteCode(newUniqueInviteCode());
        DiaryRoom savedRoom = diaryRoomRepository.save(room);

        // 방 생성을 요청한 유저를 해당 방의 방장(OWNER)으로 자동 등록
        diaryRoomRepository.insertOwner(savedRoom.getId(), memberId);

        return new DiaryRoomDto.Response(savedRoom);
    }

    // 공유방 조회
    @Transactional(readOnly = true)
    public List<DiaryRoomDto.Response> getMyRooms(Long memberId) {
        return diaryRoomRepository.findAllByMemberId(memberId).stream()
                .map(DiaryRoomDto.Response::new)
                .toList();
    }

    // 공유방 수정
    @Transactional
    public DiaryRoomDto.Response updateRoom(Long memberId, Long roomId, DiaryRoomDto.Request requestDto) {
        DiaryRoom room = getRoom(roomId);

        // 방장 권한 체크
        if (!room.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        room.update(requestDto.name(), requestDto.description());
        return new DiaryRoomDto.Response(room);
    }

    // 공유방 삭제
    @Transactional
    public void deleteRoom(Long memberId, Long roomId) {
        DiaryRoom room = getRoom(roomId);

        // 방장 권한 체크
        if (!room.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // DB FK에 ON DELETE 설정이 없어서, 딸린 데이터를 먼저 정리해야 FK 오류가 나지 않는다.
        // 일기는 항상 방 안에 있어야 하므로 방의 일기도 함께 지운다: 반응 → 댓글 → 일기 → 멤버 → 방
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

    /** 멤버 추가(초대). 방장만 할 수 있다. */
    @Transactional
    public DiaryRoomDto.MembershipResponse addMember(Long memberId, Long roomId, Long targetUserId) {
        DiaryRoom room = getRoom(roomId);
        if (!room.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // users FK가 없어서 DB가 막아주지 않으므로, 없는 사용자는 여기서 막는다.
        if (!userRepository.existsById(targetUserId)) {
            throw new UserNotFoundException(targetUserId);
        }

        // 이미 멤버(0행 추가)여도 에러 없이 진행한다 (멱등).
        diaryRoomRepository.insertMemberIfAbsent(roomId, targetUserId);
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
        diaryRoomRepository.deleteMember(roomId, targetUserId);
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

    /** 초대 코드 조회. 방장·멤버만 볼 수 있다. 코드가 없는 기존 방이면 이때 발급한다. */
    @Transactional
    public DiaryRoomDto.InviteCodeResponse getInviteCode(Long memberId, Long roomId) {
        DiaryRoom room = getRoom(roomId);
        if (!room.isOwnedBy(memberId) && !diaryRoomRepository.isMember(roomId, memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (room.getInviteCode() == null) {
            room.changeInviteCode(newUniqueInviteCode());
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
     * 초대 코드로 참여. 코드를 아는 사람은 누구나 들어올 수 있다 (인원 제한 없음).
     * 이미 멤버면 아무것도 하지 않고 200 (멱등). 대소문자·앞뒤 공백은 무시한다.
     */
    @Transactional
    public DiaryRoomDto.MembershipResponse joinByInviteCode(Long memberId, String rawInviteCode) {
        String inviteCode = rawInviteCode.strip().toUpperCase(Locale.ROOT);
        DiaryRoom room = diaryRoomRepository.findByInviteCode(inviteCode)
                .orElseThrow(DiaryRoomInviteCodeNotFoundException::new);

        diaryRoomRepository.insertMemberIfAbsent(room.getId(), memberId);
        long memberCount = diaryRoomRepository.countMembers(room.getId());
        return new DiaryRoomDto.MembershipResponse(room.getId(), memberId, true, memberCount);
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

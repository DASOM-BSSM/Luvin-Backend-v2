package com.luvin.diary.service;

import com.luvin.common.exception.BusinessException;
import com.luvin.common.exception.DiaryRoomNotFoundException;
import com.luvin.common.exception.DiaryRoomOwnerCannotLeaveException;
import com.luvin.common.exception.ErrorCode;
import com.luvin.common.exception.UserNotFoundException;
import com.luvin.diary.domain.DiaryRoom;
import com.luvin.diary.dto.DiaryDto;
import com.luvin.diary.dto.DiaryRoomDto;
import com.luvin.diary.repository.DiaryRepository;
import com.luvin.diary.repository.DiaryRoomRepository;
import com.luvin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DiaryRoomService {
    private final DiaryRoomRepository diaryRoomRepository;
    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;

    private DiaryRoom getRoom(Long roomId) {
        return diaryRoomRepository.findById(roomId)
                .orElseThrow(() -> new DiaryRoomNotFoundException(roomId));
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
}

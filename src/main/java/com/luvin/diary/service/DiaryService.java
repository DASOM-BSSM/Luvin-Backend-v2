package com.luvin.diary.service;

import com.luvin.common.exception.BusinessException;
import com.luvin.common.exception.DiaryNotFoundException;
import com.luvin.common.exception.ErrorCode;
import com.luvin.diary.domain.Diary;
import com.luvin.common.exception.DiaryRoomNotFoundException;
import com.luvin.diary.domain.DiaryRoom;
import com.luvin.diary.domain.Emoji;
import com.luvin.diary.dto.DiaryDto;
import com.luvin.diary.repository.DiaryCommentRepository;
import com.luvin.diary.repository.DiaryRepository;
import com.luvin.diary.repository.DiaryRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final DiaryRoomRepository diaryRoomRepository;
    private final DiaryCommentRepository diaryCommentRepository;

    private static final int MY_ROOMS_FEED_LIMIT = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    // 일기 생성: 일기는 항상 공유방 안에서 쓴다. 그 방의 방장·멤버만 쓸 수 있다.
    @Transactional
    public DiaryDto.Response createDiary(Long memberId, DiaryDto.CreateRequest request) {
        DiaryRoom room = diaryRoomRepository.findById(request.roomId())
                .orElseThrow(() -> new DiaryRoomNotFoundException(request.roomId()));
        if (!room.isOwnedBy(memberId) && !diaryRoomRepository.isMember(room.getId(), memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Diary savedDiary = diaryRepository.save(new Diary(memberId, room, request.title(), request.content()));
        return DiaryDto.Response.of(savedDiary, memberId);
    }

    // 일기 전체 조회
    @Transactional(readOnly = true)
    public List<DiaryDto.Response> getAllDiaries(Long memberId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        return diaryRepository
                .findAllByUserIdOrderByCreatedAtDesc(
                        memberId,
                        PageRequest.of(safePage, safeSize)
                )
                .stream()
                .map(diary -> DiaryDto.Response.of(diary, memberId))
                .toList();
    }

    // 일기 상세 조회
    @Transactional(readOnly = true)
    public DiaryDto.Response getDiary(Long memberId, Long diaryId) {
        Diary diary = getDiaryEntity(diaryId);

        // 조회 권한 체크 (본인 작성 일기이거나, 일기가 속한 방의 방장·멤버인지 검증)
        if (!diary.canBeViewedBy(memberId, () -> diaryRoomRepository.isMember(diary.getRoom().getId(), memberId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return DiaryDto.Response.of(diary, memberId);
    }

    @Transactional
    public DiaryDto.Response updateDiary(Long memberId, Long diaryId, DiaryDto.UpdateRequest request) {
        Diary diary = getOwnedDiaryOrThrow(memberId, diaryId);

        diary.update(request.title(), request.content());
        // @UpdateTimestamp는 flush 시점에 채워지므로, 응답의 updatedAt이 수정 시각이 되도록 먼저 반영한다.
        diaryRepository.flush();
        return DiaryDto.Response.of(diary, memberId);
    }

    @Transactional
    public void deleteDiary(Long memberId, Long diaryId) {
        Diary diary = getOwnedDiaryOrThrow(memberId, diaryId);

        // DB FK에 ON DELETE CASCADE가 없어서, 딸린 공감/댓글을 먼저 지워야 FK 오류가 나지 않는다.
        diaryRepository.deleteReactionsByDiaryId(diary.getId());
        diaryCommentRepository.deleteAllByDiaryId(diary.getId());
        diaryRepository.deleteById(diary.getId());
    }

    private Diary getOwnedDiaryOrThrow(Long memberId, Long diaryId) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new DiaryNotFoundException(diaryId));
        if (!diary.isWrittenBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return diary;
    }

    private Diary getDiary(Long diaryId) {
        return diaryRepository.findById(diaryId)
                .orElseThrow(() -> new DiaryNotFoundException(diaryId));
    }

    /**
     * 이모지 반응 토글.
     * 같은 이모지를 다시 누르면 취소, 반응이 없으면 추가, 다른 이모지를 누르면 그 이모지로 바뀐다.
     * 취소는 권한 확인 전에 해서, 방에서 나간 뒤에도 자기 반응은 항상 지울 수 있게 한다.
     */
    @Transactional
    public DiaryDto.ReactionResponse toggleReaction(Long memberId, Long diaryId, String rawEmoji) {
        Diary diary = getDiary(diaryId);
        String emoji = Emoji.normalize(rawEmoji);

        if (diaryRepository.deleteReactionIfSame(diaryId, memberId, emoji) > 0) {
            long likeCount = diaryRepository.countReactions(diaryId);
            return new DiaryDto.ReactionResponse(diaryId, null, false, likeCount);
        }

        if (!diary.canBeViewedBy(memberId, () -> diaryRoomRepository.isMember(diary.getRoom().getId(), memberId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        diaryRepository.upsertReaction(diaryId, memberId, emoji);
        long likeCount = diaryRepository.countReactions(diaryId);
        return new DiaryDto.ReactionResponse(diaryId, emoji, true, likeCount);
    }

    /** 내가 속한 모든 방의 일기 최신순 (최대 50개). */
    @Transactional(readOnly = true)
    public List<DiaryDto.FeedItem> getMyRoomsFeed(Long memberId) {
        return diaryRepository.findMyRoomsFeed(memberId, PageRequest.of(0, MY_ROOMS_FEED_LIMIT));
    }

    private Diary getDiaryEntity(Long diaryId) {
        return diaryRepository.findById(diaryId)
                .orElseThrow(() -> new DiaryNotFoundException(diaryId));
    }
}

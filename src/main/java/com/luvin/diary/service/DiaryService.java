package com.luvin.diary.service;

import com.luvin.common.exception.BusinessException;
import com.luvin.common.exception.DiaryNotFoundException;
import com.luvin.common.exception.ErrorCode;
import com.luvin.diary.domain.Diary;
import com.luvin.diary.domain.DiaryVisibility;
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

    private static final int COMMUNITY_FEED_LIMIT = 50;

    @Transactional
    public DiaryDto.Response updateDiary(Long memberId, Long diaryId, DiaryDto.Request request) {
        Diary diary = getOwnedDiaryOrThrow(memberId, diaryId);
        validateVisibility(diary, request.visibility());

        diary.update(request.title(), request.content(), request.visibility());
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

    /** ROOM 공개는 공유방에 올린 일기에만 의미가 있다. */
    private void validateVisibility(Diary diary, DiaryVisibility visibility) {
        if (visibility == DiaryVisibility.ROOM && diary.getRoom() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private Diary getDiary(Long diaryId) {
        return diaryRepository.findById(diaryId)
                .orElseThrow(() -> new DiaryNotFoundException(diaryId));
    }

    @Transactional
    public DiaryDto.ReactionResponse react(Long memberId,Long diaryId) {
        Diary diary = getDiary(diaryId);
        if (diary.canBeViewedBy(memberId,() -> diaryRoomRepository.isMember(diary.getRoom().getId(), memberId))) {
            diaryRepository.insertReactionIfAbsent(diary.getId(), memberId);
        }
        else {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        long likeCount = diaryRepository.countReactions(diary.getId());
        return new DiaryDto.ReactionResponse(diary.getId(), true, likeCount);
    }

    @Transactional
    public DiaryDto.ReactionResponse unreact(Long memberId,Long diaryId) {
        Diary diary = getDiary(diaryId);
        diaryRepository.deleteReaction(diary.getId(),memberId);
        long likeCount = diaryRepository.countReactions(diaryId);
        return new DiaryDto.ReactionResponse(diaryId, false, likeCount);
    }

    @Transactional(readOnly = true)
    public List<DiaryDto.FeedItem> getCommunityFeed(Long memberId) {
        return diaryRepository.findCommunityFeed(memberId, PageRequest.of(0, COMMUNITY_FEED_LIMIT));
    }
}

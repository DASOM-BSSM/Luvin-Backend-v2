package com.luvin.diary.service;

import com.luvin.common.exception.BusinessException;
import com.luvin.common.exception.ErrorCode;
import com.luvin.diary.domain.Diary;
import com.luvin.diary.domain.DiaryComment;
import com.luvin.diary.dto.DiaryCommentDto;
import com.luvin.diary.repository.DiaryCommentRepository;
import com.luvin.diary.repository.DiaryRepository;
import com.luvin.diary.repository.DiaryRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DiaryCommentService {

    private final DiaryCommentRepository diaryCommentRepository;
    private final DiaryRepository diaryRepository;
    private final DiaryRoomRepository diaryRoomRepository;

    // 1. 댓글 작성
    @Transactional
    public DiaryCommentDto.Response createComment(Long memberId, Long diaryId, DiaryCommentDto.Request requestDto) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!diary.canBeViewedBy(memberId, () -> diaryRoomRepository.isMember(diary.getRoom().getId(), memberId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        DiaryComment comment = new DiaryComment(memberId, requestDto.getContent(), diary);
        DiaryComment savedComment = diaryCommentRepository.save(comment);

        return new DiaryCommentDto.Response(savedComment);
    }

    // 2. 댓글 목록 조회
    @Transactional(readOnly = true)
    public List<DiaryCommentDto.Response> getComments(Long memberId, Long diaryId) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!diary.canBeViewedBy(memberId, () -> diaryRoomRepository.isMember(diary.getRoom().getId(), memberId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return diaryCommentRepository.findByDiaryId(diaryId).stream()
                .map(DiaryCommentDto.Response::new)
                .toList();
    }

    // 3. 댓글 수정
    @Transactional
    public DiaryCommentDto.Response updateComment(Long memberId, Long diaryId, Long commentId, DiaryCommentDto.Request requestDto) {
        DiaryComment comment = diaryCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!comment.getDiary().getId().equals(diaryId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (!comment.getUserId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        comment.update(requestDto.getContent());
        return new DiaryCommentDto.Response(comment);
    }

    // 4. 댓글 삭제
    @Transactional
    public void deleteComment(Long memberId, Long diaryId, Long commentId) {
        DiaryComment comment = diaryCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!comment.getDiary().getId().equals(diaryId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (!comment.getUserId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        diaryCommentRepository.delete(comment);
    }

}
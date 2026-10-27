package com.luvin.diary.service;

import com.luvin.common.exception.BusinessException;
import com.luvin.common.exception.ErrorCode;
import com.luvin.diary.domain.Diary;
import com.luvin.diary.domain.DiaryComment;
import com.luvin.diary.dto.DiaryCommentDto;
import com.luvin.diary.repository.DiaryCommentRepository;
import com.luvin.diary.repository.DiaryRepository;
import com.luvin.diary.repository.DiaryRoomRepository;
import com.luvin.learning.domain.LearningSourceType;
import com.luvin.learning.service.LearningSignalService;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiaryCommentService {

    private final DiaryCommentRepository diaryCommentRepository;
    private final DiaryRepository diaryRepository;
    private final DiaryRoomRepository diaryRoomRepository;
    private final LearningSignalService learningSignalService;
    private final UserRepository userRepository;

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
        learningSignalService.record(memberId, LearningSourceType.COMMENT, savedComment.getId());

        return DiaryCommentDto.Response.of(savedComment, userRepository.findById(memberId).orElse(null), memberId);
    }

    // 2. 댓글 목록 조회
    @Transactional(readOnly = true)
    public List<DiaryCommentDto.Response> getComments(Long memberId, Long diaryId) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!diary.canBeViewedBy(memberId, () -> diaryRoomRepository.isMember(diary.getRoom().getId(), memberId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<DiaryComment> comments = diaryCommentRepository.findByDiaryIdOrderByCreatedAtAsc(diaryId);
        // 작성자는 FK 없는 user_id라 한 번에 모아 조회한다 (댓글마다 조회하면 N+1).
        Map<Long, User> authors = userRepository.findAllById(
                        comments.stream().map(DiaryComment::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return comments.stream()
                .map(c -> DiaryCommentDto.Response.of(c, authors.get(c.getUserId()), memberId))
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
        return DiaryCommentDto.Response.of(comment, userRepository.findById(memberId).orElse(null), memberId);
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

        learningSignalService.remove(LearningSourceType.COMMENT, comment.getId());
        diaryCommentRepository.delete(comment);
    }

}
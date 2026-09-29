package com.luvin.diary.service;

import com.luvin.diary.domain.Diary;
import com.luvin.diary.domain.DiaryComment;
import com.luvin.diary.dto.DiaryCommentDto;
import com.luvin.diary.repository.DiaryCommentRepository;
import com.luvin.diary.repository.DiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiaryCommentService {

    private final DiaryCommentRepository diaryCommentRepository;
    private final DiaryRepository diaryRepository;

    // 1. 댓글 작성
    @Transactional
    public DiaryCommentDto.Response createComment(Long diaryId, DiaryCommentDto.Request requestDto) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new IllegalArgumentException("해당 일기가 존재하지 않습니다. id=" + diaryId));
        // TODO : new DiaryComment() 호출 시 필수값인 userId 인자 추가
        DiaryComment comment = new DiaryComment(requestDto.getContent(), diary);
        diaryCommentRepository.save(comment);
        return new DiaryCommentDto.Response(comment);
    }

    // 2. 댓글 조회
    @Transactional(readOnly = true)
    public List<DiaryCommentDto.Response> getComments(Long diaryId) {
        return diaryCommentRepository.findByDiaryId(diaryId).stream()
                .map(DiaryCommentDto.Response::new)
                .collect(Collectors.toList());
    }

    // 3. 댓글 수정
    @Transactional
    public Long updateComment(Long commentId, DiaryCommentDto.Request requestDto) {
        DiaryComment comment = diaryCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 댓글이 존재하지 않습니다. id=" + commentId));
        comment.update(requestDto.getContent());
        return commentId;
    }

    // 4. 댓글 삭제
    @Transactional
    public Long deleteComment(Long commentId) {
        diaryCommentRepository.deleteById(commentId);
        return commentId;
    }
}

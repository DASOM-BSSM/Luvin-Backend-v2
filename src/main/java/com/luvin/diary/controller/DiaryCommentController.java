package com.luvin.diary.controller;

import com.luvin.diary.dto.DiaryCommentDto;
import com.luvin.diary.service.DiaryCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/diaries/{diaryId}/comments")
public class DiaryCommentController {

    private final DiaryCommentService diaryCommentService;

    // 1. 댓글 작성 (POST /api/diaries/{diaryId}/comments)
    @PostMapping
    public DiaryCommentDto.Response createComment(@PathVariable Long diaryId, @RequestBody DiaryCommentDto.Request requestDto) {
        return diaryCommentService.createComment(diaryId, requestDto);
    }

    // 2. 댓글 조회 (GET /api/diaries/{diaryId}/comments)
    @GetMapping
    public List<DiaryCommentDto.Response> getComments(@PathVariable Long diaryId) {
        return diaryCommentService.getComments(diaryId);
    }

    // 3. 댓글 수정 (PUT /api/diaries/{diaryId}/comments/{commentId})
    @PutMapping("/{commentId}")
    public Long updateComment(@PathVariable Long diaryId, @PathVariable Long commentId, @RequestBody DiaryCommentDto.Request requestDto) {
        return diaryCommentService.updateComment(commentId, requestDto);
    }

    // 4. 댓글 삭제 (DELETE /api/diaries/{diaryId}/comments/{commentId})
    @DeleteMapping("/{commentId}")
    public Long deleteComment(@PathVariable Long diaryId, @PathVariable Long commentId) {
        return diaryCommentService.deleteComment(commentId);
    }
}

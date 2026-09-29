package com.luvin.diary.controller;

import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.diary.dto.DiaryCommentDto;
import com.luvin.diary.service.DiaryCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/diaries/{diaryId}/comments")
@RequiredArgsConstructor
public class DiaryCommentController {

    private final DiaryCommentService diaryCommentService;

    // 1. 댓글 작성
    @PostMapping
    public ApiResponse<DiaryCommentDto.Response> createComment(
            @PathVariable Long diaryId,
            @RequestBody DiaryCommentDto.Request requestDto) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryCommentService.createComment(memberId, diaryId, requestDto));
    }

    // 2. 댓글 목록 조회
    @GetMapping
    public ApiResponse<List<DiaryCommentDto.Response>> getComments(@PathVariable Long diaryId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryCommentService.getComments(memberId, diaryId));
    }

    // 3. 댓글 수정
    @PutMapping("/{commentId}")
    public ApiResponse<DiaryCommentDto.Response> updateComment(
            @PathVariable Long diaryId,
            @PathVariable Long commentId,
            @RequestBody DiaryCommentDto.Request requestDto) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryCommentService.updateComment(memberId, diaryId, commentId, requestDto));
    }

    // 4. 댓글 삭제
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> deleteComment(
            @PathVariable Long diaryId,
            @PathVariable Long commentId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        diaryCommentService.deleteComment(memberId, diaryId, commentId);
        return ApiResponse.ok(null);
    }
}
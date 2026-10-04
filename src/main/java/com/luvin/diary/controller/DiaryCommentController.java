package com.luvin.diary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.diary.dto.DiaryCommentDto;
import com.luvin.diary.service.DiaryCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "감정일기 댓글", description = "일기 댓글 작성·조회·수정·삭제")
@RestController
@RequestMapping("/api/diaries/{diaryId}/comments")
@RequiredArgsConstructor
public class DiaryCommentController {

    private final DiaryCommentService diaryCommentService;

    @Operation(summary = "댓글 작성", description = "일기를 볼 수 있는 사람만 가능 (403).")
    @PostMapping
    public ApiResponse<DiaryCommentDto.Response> createComment(
            @PathVariable Long diaryId,
            @Valid @RequestBody DiaryCommentDto.Request requestDto) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryCommentService.createComment(memberId, diaryId, requestDto));
    }

    @Operation(summary = "댓글 목록", description = "일기를 볼 수 있는 사람만 가능 (403). 작성순.")
    @GetMapping
    public ApiResponse<List<DiaryCommentDto.Response>> getComments(@PathVariable Long diaryId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryCommentService.getComments(memberId, diaryId));
    }

    @Operation(summary = "댓글 수정", description = "댓글 작성자만 가능 (403).")
    @PutMapping("/{commentId}")
    public ApiResponse<DiaryCommentDto.Response> updateComment(
            @PathVariable Long diaryId,
            @PathVariable Long commentId,
            @Valid @RequestBody DiaryCommentDto.Request requestDto) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryCommentService.updateComment(memberId, diaryId, commentId, requestDto));
    }

    @Operation(summary = "댓글 삭제", description = "댓글 작성자만 가능 (403).")
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> deleteComment(
            @PathVariable Long diaryId,
            @PathVariable Long commentId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        diaryCommentService.deleteComment(memberId, diaryId, commentId);
        return ApiResponse.ok(null);
    }
}
package com.luvin.diary.controller;

import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.diary.dto.DiaryDto;
import com.luvin.diary.service.DiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/diaries")
public class DiaryController {

    private final DiaryService diaryService;

    // 1. 일기 작성 (POST /api/diaries)
    @PostMapping
    public ApiResponse<DiaryDto.Response> createDiary(@Valid @RequestBody DiaryDto.Request request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.createDiary(memberId, request));
    }

    // 2. 전체/개인 일기 목록 조회 (GET /api/diaries)
    @GetMapping
    public ApiResponse<List<DiaryDto.Response>> getAllDiaries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.getAllDiaries(memberId, page, size));
    }

    // 3. 일기 상세 조회 (GET /api/diaries/{diaryId})
    @GetMapping("/{diaryId}")
    public ApiResponse<DiaryDto.Response> getDiary(@PathVariable Long diaryId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.getDiary(memberId, diaryId));
    }

    @PutMapping("/{diaryId}")
    public ApiResponse<DiaryDto.Response> updateDiary(@PathVariable Long diaryId,
                                                      @Valid @RequestBody DiaryDto.Request request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.updateDiary(memberId, diaryId, request));
    }

    @DeleteMapping("/{diaryId}")
    public ApiResponse<Void> deleteDiary(@PathVariable Long diaryId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        diaryService.deleteDiary(memberId, diaryId);
        return ApiResponse.okMessage("일기가 삭제되었습니다.");
    }

    @PostMapping("/{diaryId}/like")
    public ApiResponse<DiaryDto.ReactionResponse> toggleLike(@PathVariable Long diaryId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.toggleLike(memberId, diaryId));
    }

    @PutMapping("/{diaryId}/visibility")
    public ApiResponse<DiaryDto.Response> updateVisibility(@PathVariable Long diaryId, @Valid @RequestBody DiaryDto.VisibilityRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.updateVisibility(memberId, diaryId, request.visibility()));

    }

    /*
    GET /api/diaries/community
        memberId = SecurityUtils.getCurrentUserId()
        return ApiResponse.ok(diaryService.getCommunityFeed(memberId))
     */
    @GetMapping("/community")
    public ApiResponse<List<DiaryDto.FeedItem>> findCommunityFeed() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.getCommunityFeed(memberId));
    }
}

package com.luvin.diary.controller;

import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.diary.dto.DiaryDto;
import com.luvin.diary.service.DiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/diaries")
public class DiaryController {

    private final DiaryService diaryService;

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

    @PostMapping("/{diaryId}/reactions")
    public ApiResponse<DiaryDto.ReactionResponse> react(@PathVariable Long diaryId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.react(memberId, diaryId));
    }

    @DeleteMapping("/{diaryId}/reactions")
    public ApiResponse<DiaryDto.ReactionResponse> unreact(@PathVariable Long diaryId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.unreact(memberId, diaryId));
    }
}

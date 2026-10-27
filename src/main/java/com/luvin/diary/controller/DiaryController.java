package com.luvin.diary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.diary.dto.DiaryDto;
import com.luvin.diary.service.DiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "감정일기", description = "일기 작성·조회·수정·삭제, 이모지 반응, 내 방들 피드")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/diaries")
public class DiaryController {

    private final DiaryService diaryService;

    @Operation(summary = "일기 작성", description = "일기는 항상 공유방 안에서 쓴다. 그 방의 방장·멤버만 작성 가능 (아니면 403, 없는 방 404).")
    @PostMapping
    public ApiResponse<DiaryDto.Response> createDiary(@Valid @RequestBody DiaryDto.CreateRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.createDiary(memberId, request));
    }

    @Operation(summary = "내 일기 목록", description = "내가 쓴 일기만 최신순. size 최대 50.")
    @GetMapping
    public ApiResponse<List<DiaryDto.Response>> getAllDiaries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.getAllDiaries(memberId, page, size));
    }

    @Operation(summary = "내 일기 달력", description = """
            year년 month월(한국 시간 기준으로 그 달 1일 00:00 ~ 다음 달 1일 00:00 전)에 내가 쓴 일기의 날짜와 id.
            모든 공유방의 내 일기가 대상이고 본문은 주지 않는다(상세는 GET /api/diaries/{diaryId}).

            - 날짜(`date`, yyyy-MM-dd)는 작성 시각을 한국 시간으로 바꾼 날짜이고, 오름차순이다.
            - 하루에 여러 개면 그날 항목 하나의 `diaryIds`에 작성순(오래된 것 먼저)으로 모두 담는다.
            - 일기가 없는 날은 목록에 없다. 그 달에 일기가 없으면 빈 배열.
            - month가 1~12가 아니면 400.
            """)
    @GetMapping("/calendar")
    public ApiResponse<List<DiaryDto.CalendarDay>> getMyCalendar(@RequestParam int year, @RequestParam int month) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.getMyCalendar(memberId, year, month));
    }

    @Operation(summary = "일기 상세", description = "작성자 또는 일기가 속한 방의 방장·멤버만 조회 가능 (아니면 403).")
    @GetMapping("/{diaryId}")
    public ApiResponse<DiaryDto.Response> getDiary(@PathVariable Long diaryId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.getDiary(memberId, diaryId));
    }

    @Operation(summary = "일기 수정", description = "작성자만 가능. 제목·내용만 수정 (방은 옮길 수 없음).")
    @PutMapping("/{diaryId}")
    public ApiResponse<DiaryDto.Response> updateDiary(@PathVariable Long diaryId,
                                                      @Valid @RequestBody DiaryDto.UpdateRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.updateDiary(memberId, diaryId, request));
    }

    @Operation(summary = "일기 삭제", description = "작성자만 가능. 달린 반응·댓글도 함께 삭제.")
    @DeleteMapping("/{diaryId}")
    public ApiResponse<Void> deleteDiary(@PathVariable Long diaryId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        diaryService.deleteDiary(memberId, diaryId);
        return ApiResponse.okMessage("일기가 삭제되었습니다.");
    }

    @Operation(summary = "이모지 반응 토글", description = "이모지 문자 그대로 전송. 같은 이모지 다시 누르면 취소, 다른 이모지면 교체. 한 사람당 일기 하나에 반응 1개. 이모지 1개가 아니면 400.")
    @PostMapping("/{diaryId}/like")
    public ApiResponse<DiaryDto.ReactionResponse> toggleReaction(@PathVariable Long diaryId,
                                                                 @Valid @RequestBody DiaryDto.ReactionRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.toggleReaction(memberId, diaryId, request.emoji()));
    }

    @Operation(summary = "내 방들 피드", description = "내가 방장이거나 멤버인 모든 공유방의 일기를 최신순으로 최대 50개.")
    @GetMapping("/community")
    public ApiResponse<List<DiaryDto.FeedItem>> getMyRoomsFeed() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryService.getMyRoomsFeed(memberId));
    }
}

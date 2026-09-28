package com.luvin.diary.controller;

import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.diary.domain.Diary;
import com.luvin.diary.dto.DiaryDto;
import com.luvin.diary.dto.DiaryRoomDto;
import com.luvin.diary.service.DiaryRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/diary-rooms")
public class DiaryRoomController {

    private final DiaryRoomService diaryRoomService;

    /*
    DELETE /api/diary-rooms/{roomId}/members/me → leave       (body 없음)
    POST   /api/diary-rooms/{roomId}/members → addMember   (@Valid @RequestBody AddMemberRequest)
    DELETE /api/diary-rooms/{roomId}/members/{userId} → kickMember  (body 없음)
     */
    @DeleteMapping("/{roomId}/members/me")
    public ApiResponse<DiaryRoomDto.MembershipResponse> leave(@PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.leave(memberId, roomId));
    }

    @PostMapping("/{roomId}/members")
    public ApiResponse<DiaryRoomDto.MembershipResponse> addMember(@Valid @RequestBody DiaryRoomDto.AddMemberRequest request, @PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.addMember(memberId, roomId, request.userId()));
    }

    @DeleteMapping("/{roomId}/members/kick")
    public ApiResponse<DiaryRoomDto.KickResponse> kickMember(@PathVariable Long roomId, @RequestParam Long userId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.kickMember(memberId,roomId,userId));
    }

    /*
    GET /api/diary-rooms/{roomId}/diaries
      memberId = SecurityUtils.getCurrentUserId()
      return ApiResponse.ok(diaryRoomService.getRoomDiaries(memberId, roomId))
     */
    @GetMapping("/{roomId}/diaries")
    public ApiResponse<List<DiaryDto.FeedItem>> getRoomDiaries(@PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.getRoomDiaries(memberId, roomId));
    }
}

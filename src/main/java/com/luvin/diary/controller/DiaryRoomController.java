package com.luvin.diary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "감정일기 공유방", description = "공유방 생성·조회·수정·삭제, 멤버 관리(초대 방식), 방 일기 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/diary-rooms")
public class DiaryRoomController {

    private final DiaryRoomService diaryRoomService;

    @Operation(summary = "공유방 생성", description = "만든 사람이 방장(OWNER)으로 자동 등록.")
    @PostMapping
    public ApiResponse<DiaryRoomDto.Response> createRoom(@Valid @RequestBody DiaryRoomDto.Request requestDto) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.createRoom(memberId, requestDto));
    }

    @Operation(summary = "내 공유방 목록", description = "내가 방장이거나 멤버인 방. 최근 참여한 방이 먼저.")
    @GetMapping
    public ApiResponse<List<DiaryRoomDto.Response>> getMyRooms() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.getMyRooms(memberId));
    }

    @Operation(summary = "공유방 수정", description = "방장만 가능 (403).")
    @PutMapping("/{roomId}")
    public ApiResponse<DiaryRoomDto.Response> updateRoom(
            @PathVariable Long roomId,
            @Valid @RequestBody DiaryRoomDto.Request requestDto) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.updateRoom(memberId, roomId, requestDto));
    }

    @Operation(summary = "공유방 삭제", description = "방장만 가능. 방의 일기·반응·댓글·멤버도 함께 삭제.")
    @DeleteMapping("/{roomId}")
    public ApiResponse<Void> deleteRoom(@PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        diaryRoomService.deleteRoom(memberId, roomId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "멤버 목록", description = "방장·멤버만 조회 가능 (403).")
    @GetMapping("/{roomId}/members")
    public ApiResponse<List<DiaryRoomDto.MemberResponse>> getRoomMembers(@PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.getRoomMembers(memberId, roomId));
    }

    @Operation(summary = "공유방 나가기", description = "방장은 나갈 수 없음 (400). 이미 나간 상태여도 200.")
    @DeleteMapping("/{roomId}/members/me")
    public ApiResponse<DiaryRoomDto.MembershipResponse> leave(@PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.leave(memberId, roomId));
    }

    @Operation(summary = "멤버 추가(초대)", description = "방장만 가능 (403). 없는 사용자 404. 이미 멤버여도 200.")
    @PostMapping("/{roomId}/members")
    public ApiResponse<DiaryRoomDto.MembershipResponse> addMember(@Valid @RequestBody DiaryRoomDto.AddMemberRequest request, @PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.addMember(memberId, roomId, request.userId()));
    }

    @Operation(summary = "멤버 강퇴", description = "방장만 가능 (403). 방장 자신은 강퇴 불가 (400). 멤버가 아닌 id여도 200.")
    @DeleteMapping("/{roomId}/members/kick")
    public ApiResponse<DiaryRoomDto.KickResponse> kickMember(@PathVariable Long roomId, @RequestParam Long userId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.kickMember(memberId,roomId,userId));
    }

    @Operation(summary = "공유방 일기 조회", description = "방장·멤버만 조회 가능 (403). 최신순. 작성자 닉네임·빵 타입, 반응 수, 댓글 수, 내 반응 포함.")
    @GetMapping("/{roomId}/diaries")
    public ApiResponse<List<DiaryDto.FeedItem>> getRoomDiaries(@PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.getRoomDiaries(memberId, roomId));
    }
}

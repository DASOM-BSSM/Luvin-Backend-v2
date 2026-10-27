package com.luvin.diary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.luvin.common.response.ApiErrorView;
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

    // 오류 본문 예시. code가 null인 오류는 GlobalExceptionHandler가 ApiResponse.error(message)로 내려주는 것들이다.
    private static final String JSON = "application/json";
    private static final String BAD_REQUEST_EXAMPLE = """
            {"success": false, "data": null, "message": "최대 인원은 2~6명입니다.", "code": null}""";
    private static final String FORBIDDEN_EXAMPLE = """
            {"success": false, "data": null, "message": "권한이 없습니다.", "code": null}""";
    private static final String ROOM_NOT_FOUND_EXAMPLE = """
            {"success": false, "data": null, "message": "공유방을 찾을 수 없습니다. roomId=1", "code": null}""";
    private static final String INVITE_CODE_NOT_FOUND_EXAMPLE = """
            {"success": false, "data": null, "message": "초대 코드가 올바르지 않습니다. 다시 확인해주세요.", "code": null}""";
    private static final String ROOM_FULL_EXAMPLE = """
            {"success": false, "data": null, "message": "공유방 인원이 가득 찼습니다. (최대 2명)", "code": "DIARY_ROOM_FULL"}""";
    private static final String NICKNAME_DUPLICATED_EXAMPLE = """
            {"success": false, "data": null, "message": "이 공유방에서 이미 쓰고 있는 닉네임입니다.", "code": "DIARY_ROOM_NICKNAME_DUPLICATED"}""";
    private static final String MAX_BELOW_MEMBER_COUNT_EXAMPLE = """
            {"success": false, "data": null, "message": "최대 인원은 현재 인원(3명)보다 적게 정할 수 없습니다.", "code": "DIARY_ROOM_MAX_BELOW_MEMBER_COUNT"}""";

    @Operation(summary = "공유방 생성", description = "만든 사람이 방장(OWNER)으로 자동 등록되고 ownerNickname이 방장의 방 닉네임이 된다. "
            + "maxMemberCount는 방장 포함 2~6명(범위 밖이면 400). 응답의 memberCount는 방장 1명이라 항상 1.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "검증 실패(name·ownerNickname 공백, ownerNickname 10자 초과, maxMemberCount 누락·2~6 밖). code는 null",
                    content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                            examples = @ExampleObject(value = BAD_REQUEST_EXAMPLE)))
    })
    @PostMapping
    public ApiResponse<DiaryRoomDto.Response> createRoom(@Valid @RequestBody DiaryRoomDto.CreateRequest requestDto) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.createRoom(memberId, requestDto));
    }

    @Operation(summary = "내 공유방 목록", description = "내가 방장이거나 멤버인 방. 최근 참여한 방이 먼저.")
    @GetMapping
    public ApiResponse<List<DiaryRoomDto.Response>> getMyRooms() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.getMyRooms(memberId));
    }

    @Operation(summary = "공유방 단건 조회", description = "방장(owner_id)이거나 diary_room_member에 있는 사람만 조회 가능, "
            + "아니면 403. 없는 방은 404(권한 확인보다 먼저).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "방장·멤버가 아님(나갔거나 강퇴된 사람 포함). code는 null",
                    content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "없는 방. code는 null",
                    content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                            examples = @ExampleObject(value = ROOM_NOT_FOUND_EXAMPLE)))
    })
    @GetMapping("/{roomId}")
    public ApiResponse<DiaryRoomDto.Response> getRoom(@PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.getRoomDetail(memberId, roomId));
    }

    @Operation(summary = "공유방 수정", description = "방장만 가능 (403). maxMemberCount를 현재 인원보다 적게 하면 "
            + "409 DIARY_ROOM_MAX_BELOW_MEMBER_COUNT, ownerNickname이 다른 멤버와 겹치면 409 DIARY_ROOM_NICKNAME_DUPLICATED.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "검증 실패(생성과 같은 규칙). code는 null",
                    content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                            examples = @ExampleObject(value = BAD_REQUEST_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "방장이 아님. code는 null",
                    content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "없는 방. code는 null",
                    content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                            examples = @ExampleObject(value = ROOM_NOT_FOUND_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "code: DIARY_ROOM_MAX_BELOW_MEMBER_COUNT(maxMemberCount < 현재 인원) 또는 "
                            + "DIARY_ROOM_NICKNAME_DUPLICATED(ownerNickname이 다른 멤버와 겹침)",
                    content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                            examples = {
                                    @ExampleObject(name = "DIARY_ROOM_MAX_BELOW_MEMBER_COUNT", value = MAX_BELOW_MEMBER_COUNT_EXAMPLE),
                                    @ExampleObject(name = "DIARY_ROOM_NICKNAME_DUPLICATED", value = NICKNAME_DUPLICATED_EXAMPLE)
                            }))
    })
    @PutMapping("/{roomId}")
    public ApiResponse<DiaryRoomDto.Response> updateRoom(
            @PathVariable Long roomId,
            @Valid @RequestBody DiaryRoomDto.UpdateRequest requestDto) {
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

    @Operation(summary = "멤버 목록", description = "방장·멤버만 조회 가능 (403). 방 닉네임·역할 포함, 먼저 들어온 순.")
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

    @Operation(summary = "멤버 추가(초대)", description = "방장만 가능 (403). 없는 사용자 404. 이미 멤버여도 200(닉네임 변경 없음). "
            + "nickname은 이 방에서 쓸 닉네임(1~10자), 비우면 계정 닉네임(없으면 이름). "
            + "꽉 찼으면 409 DIARY_ROOM_FULL, 닉네임이 겹치면 409 DIARY_ROOM_NICKNAME_DUPLICATED.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "code: DIARY_ROOM_FULL(현재 인원 >= maxMemberCount) 또는 DIARY_ROOM_NICKNAME_DUPLICATED",
                    content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                            examples = {
                                    @ExampleObject(name = "DIARY_ROOM_FULL", value = ROOM_FULL_EXAMPLE),
                                    @ExampleObject(name = "DIARY_ROOM_NICKNAME_DUPLICATED", value = NICKNAME_DUPLICATED_EXAMPLE)
                            }))
    })
    @PostMapping("/{roomId}/members")
    public ApiResponse<DiaryRoomDto.MembershipResponse> addMember(@Valid @RequestBody DiaryRoomDto.AddMemberRequest request, @PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.addMember(memberId, roomId, request.userId(), request.nickname()));
    }

    @Operation(summary = "멤버 강퇴", description = "방장만 가능 (403). 방장 자신은 강퇴 불가 (400). 멤버가 아닌 id여도 200. 실제로 강퇴되면 초대 코드가 새로 발급되어 이전 코드는 쓸 수 없다.")
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

    @Operation(summary = "초대 코드 조회", description = "방장·멤버만 조회 가능 (403). 코드는 영구이며 공유해서 다른 사람을 초대한다.")
    @GetMapping("/{roomId}/invite-code")
    public ApiResponse<DiaryRoomDto.InviteCodeResponse> getInviteCode(@PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.getInviteCode(memberId, roomId));
    }

    @Operation(summary = "초대 코드 재발급", description = "방장만 가능 (403). 이전 코드는 바로 쓸 수 없게 된다.")
    @PostMapping("/{roomId}/invite-code")
    public ApiResponse<DiaryRoomDto.InviteCodeResponse> regenerateInviteCode(@PathVariable Long roomId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.regenerateInviteCode(memberId, roomId));
    }

    @Operation(summary = "초대 코드로 참여", description = "코드를 아는 사람은 최대 인원까지 참여 가능. 대소문자 구분 없음. "
            + "nickname은 이 방에서 쓸 닉네임. 이미 멤버면 200(닉네임 변경 없음), 없는 코드면 404, "
            + "인원이 가득 찼으면 409 DIARY_ROOM_FULL, 같은 방에 같은 닉네임이 있으면 409 DIARY_ROOM_NICKNAME_DUPLICATED.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true,
                    description = "참여 완료 또는 이미 멤버(멱등)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "검증 실패(inviteCode·nickname 공백, nickname 10자 초과). code는 null",
                    content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "없는 초대 코드(재발급·강퇴로 바뀐 이전 코드 포함). code는 null",
                    content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                            examples = @ExampleObject(value = INVITE_CODE_NOT_FOUND_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "code: DIARY_ROOM_FULL(현재 인원 >= maxMemberCount) 또는 DIARY_ROOM_NICKNAME_DUPLICATED",
                    content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiErrorView.class),
                            examples = {
                                    @ExampleObject(name = "DIARY_ROOM_FULL", value = ROOM_FULL_EXAMPLE),
                                    @ExampleObject(name = "DIARY_ROOM_NICKNAME_DUPLICATED", value = NICKNAME_DUPLICATED_EXAMPLE)
                            }))
    })
    @PostMapping("/join")
    public ApiResponse<DiaryRoomDto.MembershipResponse> joinByInviteCode(@Valid @RequestBody DiaryRoomDto.JoinRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(diaryRoomService.joinByInviteCode(memberId, request));
    }
}

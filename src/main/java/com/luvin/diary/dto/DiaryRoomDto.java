package com.luvin.diary.dto;

import com.luvin.diary.domain.DiaryRoom;
import com.luvin.diary.domain.DiaryRoomMember;
import com.luvin.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 공유방 요청/응답. springdoc은 중첩 클래스의 단순 이름(Request, Response …)으로 스키마를 만들어
 * 다른 DTO와 서로 덮어쓰므로, 모든 클래스에 @Schema(name = "DiaryRoom…")로 고유한 이름을 붙인다.
 */
public class DiaryRoomDto {

    public static final int MIN_MEMBER_COUNT = 2;
    public static final int MAX_MEMBER_COUNT = 6;
    public static final int NICKNAME_MAX_LENGTH = 10;

    @Schema(name = "DiaryRoomResponse", description = "공유방. 목록·단건 조회·생성·수정 공통")
    public record Response(
            Long id,
            String name,
            String description,
            Long ownerId,
            @Schema(description = "최대 인원(방장 포함)", minimum = "2", maximum = "6")
            int maxMemberCount,
            @Schema(description = "현재 인원(방장 포함)")
            long memberCount
    ) {
        public Response(DiaryRoom room, long memberCount) {
            this(room.getId(), room.getName(), room.getDescription(), room.getOwnerId(),
                    room.getMaxMemberCount(), memberCount);
        }
    }

    @Schema(name = "DiaryRoomMemberResponse")
    public record MemberResponse(
            Long id,
            String name,
            String email,
            @Schema(description = "이 방에서 쓰는 닉네임")
            String nickname,
            @Schema(allowableValues = {"OWNER", "MEMBER"})
            String role
    ) {
        public MemberResponse(DiaryRoomMember m) {
            this(m.getMember().getId(), m.getMember().getName(), m.getMember().getEmail(),
                    m.getNickname(), m.getRole().name());
        }
    }

    @Schema(name = "DiaryRoomCreateRequest")
    public record CreateRequest(
            @NotBlank(message = "공유방 이름은 필수 입력값입니다.")
            String name,

            String description,

            @Schema(description = "최대 인원(방장 포함)", minimum = "2", maximum = "6")
            @NotNull(message = "최대 인원을 선택해주세요.")
            @Min(value = MIN_MEMBER_COUNT, message = "최대 인원은 2~6명입니다.")
            @Max(value = MAX_MEMBER_COUNT, message = "최대 인원은 2~6명입니다.")
            Integer maxMemberCount,

            @Schema(description = "이 방에서 쓸 방장 닉네임. 앞뒤 공백 제외 1~10자", maxLength = NICKNAME_MAX_LENGTH)
            @NotBlank(message = "닉네임을 입력해주세요.")
            @Size(max = NICKNAME_MAX_LENGTH, message = "닉네임은 10자 이하로 입력해주세요.")
            String ownerNickname
    ) {
    }

    @Schema(name = "DiaryRoomUpdateRequest", description = "방장만 가능. 모든 필드를 다시 보낸다(PUT).")
    public record UpdateRequest(
            @NotBlank(message = "공유방 이름은 필수 입력값입니다.")
            String name,

            String description,

            @Schema(description = "최대 인원(방장 포함). 현재 인원보다 적으면 409 DIARY_ROOM_MAX_BELOW_MEMBER_COUNT",
                    minimum = "2", maximum = "6")
            @NotNull(message = "최대 인원을 선택해주세요.")
            @Min(value = MIN_MEMBER_COUNT, message = "최대 인원은 2~6명입니다.")
            @Max(value = MAX_MEMBER_COUNT, message = "최대 인원은 2~6명입니다.")
            Integer maxMemberCount,

            @Schema(description = "방장 닉네임. 앞뒤 공백 제외 1~10자. 다른 멤버와 겹치면 409 DIARY_ROOM_NICKNAME_DUPLICATED",
                    maxLength = NICKNAME_MAX_LENGTH)
            @NotBlank(message = "닉네임을 입력해주세요.")
            @Size(max = NICKNAME_MAX_LENGTH, message = "닉네임은 10자 이하로 입력해주세요.")
            String ownerNickname
    ) {
    }

    @Schema(name = "DiaryRoomAddMemberRequest")
    public record AddMemberRequest(
            @NotNull(message = "추가할 사람을 선택해주세요.")
            Long userId,

            @Schema(description = "이 방에서 쓸 닉네임. 앞뒤 공백 제외 1~10자. 비우면 계정 닉네임(없으면 이름)",
                    maxLength = NICKNAME_MAX_LENGTH, nullable = true)
            @Size(max = NICKNAME_MAX_LENGTH, message = "닉네임은 10자 이하로 입력해주세요.")
            String nickname
    ) {
    }

    @Schema(name = "DiaryRoomMembershipResponse")
    public record MembershipResponse(
            Long roomId,
            Long userId,
            boolean isMember,
            long memberCount
    ){
    }

    @Schema(name = "DiaryRoomKickResponse")
    public record KickResponse (
            Long roomId,
            Long userId,
            String message
    ){
    }

    /** 초대 코드로 참여 요청. 대소문자는 구분하지 않는다. */
    @Schema(name = "DiaryRoomJoinRequest")
    public record JoinRequest(
            @NotBlank(message = "초대 코드를 입력해주세요.")
            String inviteCode,

            @Schema(description = "이 방에서 쓸 닉네임. 앞뒤 공백 제외 1~10자", maxLength = NICKNAME_MAX_LENGTH)
            @NotBlank(message = "닉네임을 입력해주세요.")
            @Size(max = NICKNAME_MAX_LENGTH, message = "닉네임은 10자 이하로 입력해주세요.")
            String nickname
    ) {
    }

    /** 초대 코드 조회/재발급 응답. */
    @Schema(name = "DiaryRoomInviteCodeResponse")
    public record InviteCodeResponse(
            Long roomId,
            String inviteCode
    ) {
    }

    /** 방장이 userId로 직접 추가할 때 쓰는 기본 방 닉네임: 계정 닉네임, 없으면 이름(10자까지). */
    public static String defaultNickname(User user) {
        String base = user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getName();
        base = base.strip();
        if (base.length() <= NICKNAME_MAX_LENGTH) {
            return base;
        }
        // 이모지(서로게이트 쌍)가 중간에서 잘리지 않게 한다.
        int end = Character.isHighSurrogate(base.charAt(NICKNAME_MAX_LENGTH - 1)) ? NICKNAME_MAX_LENGTH - 1 : NICKNAME_MAX_LENGTH;
        return base.substring(0, end);
    }
}

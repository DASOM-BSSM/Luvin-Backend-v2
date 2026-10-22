package com.luvin.diary.dto;

import com.luvin.user.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.luvin.diary.domain.DiaryRoom;
import lombok.Getter;

public class DiaryRoomDto {

    @Getter
    public static class Response {
        private final Long id;
        private final String name;
        private final Long ownerId;

        public Response(DiaryRoom room) {
            this.id = room.getId();
            this.name = room.getName();
            this.ownerId = room.getOwnerId();
        }
    }

    @Getter
    public static class MemberResponse {
        private final Long id;
        private final String name;
        private final String email;

        public MemberResponse(User user) { // 프로젝트 유저 엔티티 타입에 맞춰 매핑
            this.id = user.getId();
            this.name = user.getName();
            this.email = user.getEmail();
        }
    }

    public record Request(
            @NotBlank(message = "공유방 이름은 필수 입력값입니다.")
            String name,
            String description
    ) {
    }

    public record AddMemberRequest(
            @NotNull(message = "추가할 사람을 선택해주세요.")
            Long userId
    ) {
    }

    public record MembershipResponse(
            Long roomId,
            Long userId,
            boolean isMember,
            long memberCount
    ){
    }

    public record KickResponse (
            Long roomId,
            Long userId,
            String message
    ){
    }

    /** 초대 코드로 참여 요청. 대소문자는 구분하지 않는다. */
    public record JoinRequest(
            @NotBlank(message = "초대 코드를 입력해주세요.")
            String inviteCode
    ) {
    }

    /** 초대 코드 조회/재발급 응답. */
    public record InviteCodeResponse(
            Long roomId,
            String inviteCode
    ) {
    }
}

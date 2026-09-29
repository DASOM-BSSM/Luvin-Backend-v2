package com.luvin.diary.dto;

import com.luvin.user.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.luvin.diary.domain.DiaryRoom;

public class DiaryRoomDto {

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
}

package com.luvin.diary.dto;

import jakarta.validation.constraints.NotNull;

public class DiaryRoomDto {

    public static class Request {
    }

    public static class Response {
    }

    public static class MemberResponse {
    }

    public record AddMemberRequest(
            @NotNull(message = "추가할 사람을 선택해주세요.")
            Long userId
    ) {
    }

    public record MembershipResponse(
            Long roomId,
            Long userId,
            boolean member,
            long memberCount
    ){
    }
}

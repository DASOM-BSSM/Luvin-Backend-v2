package com.luvin.diary.dto;

import com.luvin.diary.domain.DiaryComment;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;


public class DiaryCommentDto {

    @Getter
    @NoArgsConstructor
    public static class Request {
        @NotBlank(message = "댓글 내용은 필수 입력값입니다.")
        private String content;

        public Request(String content) {
            this.content = content;
        }
    }

    @Getter
    public static class Response {
        private Long id;

        private Long userId;
        private String content;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public Response(DiaryComment comment) {
            this.id = comment.getId();
            this.userId = comment.getUserId();
            this.content = comment.getContent();
            this.createdAt = comment.getCreatedAt();
            this.updatedAt = comment.getUpdatedAt();
        }
    }
}

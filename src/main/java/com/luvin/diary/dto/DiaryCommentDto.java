package com.luvin.diary.dto;

import com.luvin.diary.domain.DiaryComment;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


public class DiaryCommentDto {

    @Getter
    @NoArgsConstructor
    public static class Request {
        private String content;
    }

    @Getter
    public static class Response {
        private Long id;
        private String content;
        private LocalDateTime createdAt;

        public Response(DiaryComment comment) {
            this.id = comment.getId();
            this.content = comment.getContent();
            this.createdAt = comment.getCreatedAt() != null ? comment.getCreatedAt().toLocalDateTime() : null;
        }
    }
}

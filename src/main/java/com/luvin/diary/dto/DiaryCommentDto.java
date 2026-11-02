package com.luvin.diary.dto;

import com.luvin.user.service.BreadNames;
import io.swagger.v3.oas.annotations.media.Schema;
import com.luvin.diary.domain.DiaryComment;
import com.luvin.user.domain.User;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;


public class DiaryCommentDto {

    @Getter
    @NoArgsConstructor
    @Schema(name = "DiaryCommentRequest")
    public static class Request {
        @NotBlank(message = "댓글 내용은 필수 입력값입니다.")
        private String content;

        public Request(String content) {
            this.content = content;
        }
    }

    /**
     * 댓글 응답. 작성자 정보는 DiaryFeedItem과 같은 규칙이다: authorNickname은 계정 닉네임(없으면 이름),
     * authorBreadType은 빵 타입 id(예: "salt_bread", 설문 전이면 null), authorBreadName은 빵 이름(예: "쫀쫀한 카스테라",
     * 설문 전이면 null — BreadNames). 탈퇴한 작성자면 모두 null.
     */
    @Schema(name = "DiaryCommentResponse")
    public record Response(
            Long id,
            Long userId,
            String content,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String authorNickname,
            String authorBreadType,
            boolean isMine,
            @Schema(description = "작성자 빵 이름(예: \"쫀쫀한 카스테라\"). 설문 전·탈퇴면 null")
            String authorBreadName
    ) {
        public static Response of(DiaryComment comment, User author, Long currentUserId) {
            return new Response(
                    comment.getId(),
                    comment.getUserId(),
                    comment.getContent(),
                    comment.getCreatedAt(),
                    comment.getUpdatedAt(),
                    author == null ? null : (author.getNickname() != null ? author.getNickname() : author.getName()),
                    author == null ? null : author.getPersonalityType(),
                    comment.getUserId().equals(currentUserId),
                    BreadNames.of(author)
            );
        }
    }
}

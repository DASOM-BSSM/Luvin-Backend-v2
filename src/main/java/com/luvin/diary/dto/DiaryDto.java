package com.luvin.diary.dto;

import com.luvin.diary.domain.Diary;
import com.luvin.diary.domain.DiaryVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class DiaryDto {

    /** 일기 작성/수정 공통 요청. */
    public record Request(
            @NotBlank(message = "제목을 입력해주세요.")
            @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.")
            String title,

            @NotBlank(message = "내용을 입력해주세요.")
            @Size(max = 5000, message = "내용은 5000자 이하로 입력해주세요.")
            String content,

            @NotNull(message = "공개범위를 선택해주세요.")
            DiaryVisibility visibility
    ) {
    }

    /** 일기 목록/상세 공통 응답. */
    public record Response(
            Long diaryId,
            Long roomId,
            String title,
            String content,
            DiaryVisibility visibility,
            boolean isMine,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static Response of(Diary diary, Long currentUserId) {
            return new Response(
                    diary.getId(),
                    diary.getRoom() != null ? diary.getRoom().getId() : null,
                    diary.getTitle(),
                    diary.getContent(),
                    diary.getVisibility(),
                    diary.isWrittenBy(currentUserId),
                    diary.getCreatedAt(),
                    diary.getUpdatedAt()
            );
        }
    }

    /** 공감/공감취소 응답. */
    public record ReactionResponse(
            Long diaryId,
            boolean reacted,
            long likeCount
    ) {
    }
}

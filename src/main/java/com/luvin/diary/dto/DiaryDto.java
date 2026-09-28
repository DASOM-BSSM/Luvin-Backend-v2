package com.luvin.diary.dto;

import com.luvin.diary.domain.Diary;
import com.luvin.diary.domain.DiaryVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public class DiaryDto {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 응답 시간을 한국 시간(+09:00)으로 맞춘다. DB에서 읽은 값(UTC)과 방금 저장한 값(서버 시간대)이
     * 섞여 나오지 않도록, 서버 시간대 설정과 상관없이 항상 같은 형식으로 내보낸다.
     */
    private static OffsetDateTime toKst(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(KST).toOffsetDateTime();
    }

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
        public Response {
            createdAt = toKst(createdAt);
            updatedAt = toKst(updatedAt);
        }

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
            boolean liked,
            long likeCount
    ) {
    }

    /** 공유방·커뮤니티 일기 목록 항목. JPQL select new로 만들어지므로 필드 순서를 쿼리와 맞춰야 한다. */
    public record FeedItem(
            Long diaryId,
            Long roomId,
            Long authorId,
            String title,
            String content,
            DiaryVisibility visibility,
            boolean isMine,
            long likeCount,
            long commentCount,
            boolean liked,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public FeedItem {
            createdAt = toKst(createdAt);
            updatedAt = toKst(updatedAt);
        }
    }

    public record VisibilityRequest(
            @NotNull(message = "공개범위를 선택해주세요.")
            DiaryVisibility visibility
    ){
    }
}

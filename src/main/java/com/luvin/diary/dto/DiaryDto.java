package com.luvin.diary.dto;

import com.luvin.diary.domain.Diary;
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

    /** 일기 작성 요청. 일기는 항상 공유방 안에서 쓴다. */
    public record CreateRequest(
            @NotNull(message = "공유방을 선택해주세요.")
            Long roomId,

            @NotBlank(message = "제목을 입력해주세요.")
            @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.")
            String title,

            @NotBlank(message = "내용을 입력해주세요.")
            @Size(max = 5000, message = "내용은 5000자 이하로 입력해주세요.")
            String content
    ) {
    }

    /** 일기 수정 요청. 방은 옮길 수 없다. */
    public record UpdateRequest(
            @NotBlank(message = "제목을 입력해주세요.")
            @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.")
            String title,

            @NotBlank(message = "내용을 입력해주세요.")
            @Size(max = 5000, message = "내용은 5000자 이하로 입력해주세요.")
            String content
    ) {
    }

    /** 일기 목록/상세 공통 응답. */
    public record Response(
            Long diaryId,
            Long roomId,
            String title,
            String content,
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
                    diary.getRoom().getId(),
                    diary.getTitle(),
                    diary.getContent(),
                    diary.isWrittenBy(currentUserId),
                    diary.getCreatedAt(),
                    diary.getUpdatedAt()
            );
        }
    }

    /** 이모지 반응 요청. 이모지 문자 그대로 보낸다 (예: "❤️", "😂"). */
    public record ReactionRequest(
            @NotBlank(message = "이모지를 선택해주세요.")
            String emoji
    ) {
    }

    /**
     * 이모지 반응 응답.
     * emoji: 이번 요청 후 내 반응 (취소됐으면 null), liked: 반응했는지, likeCount: 모든 이모지를 합친 반응 수
     */
    public record ReactionResponse(
            Long diaryId,
            String emoji,
            boolean liked,
            long likeCount
    ) {
    }

    /**
     * 공유방·내 방들 피드 일기 목록 항목. emoji는 내 반응(없으면 null).
     * authorNickname은 닉네임이 없으면 이름, authorBreadType은 빵 타입 id(예: "salt_bread", 설문 전이면 null). JPQL select new로 만들어지므로 필드 순서를 쿼리와 맞춰야 한다. */
    public record FeedItem(
            Long diaryId,
            Long roomId,
            Long authorId,
            String authorNickname,
            String authorBreadType,
            String title,
            String content,
            boolean isMine,
            long likeCount,
            long commentCount,
            boolean liked,
            String emoji,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public FeedItem {
            createdAt = toKst(createdAt);
            updatedAt = toKst(updatedAt);
        }
    }
}

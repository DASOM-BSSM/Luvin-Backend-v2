package com.luvin.diary.dto;

import com.luvin.diary.domain.RoomQuestion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public class RoomQuestionDto {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** DiaryDto와 같이 응답 시간은 항상 한국 시간(+09:00)으로 내보낸다. */
    private static OffsetDateTime toKst(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(KST).toOffsetDateTime();
    }

    /** 그룹 질문 답하기 요청. 앞뒤 공백을 뺀 1~200자. */
    @Schema(name = "RoomQuestionAnswerRequest")
    public record AnswerRequest(
            @Schema(description = "1~200자(앞뒤 공백 제외). 아니면 400 ROOM_ANSWER_INVALID", maxLength = 200)
            String content
    ) {
    }

    /** 답 하나. authorNickname은 피드와 같이 닉네임이 없으면 이름. JPQL select new로 만들어지므로 필드 순서를 쿼리와 맞춘다. */
    @Schema(name = "RoomQuestionAnswerItem")
    public record AnswerItem(
            Long answerId,
            Long authorId,
            String authorNickname,
            String content,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public AnswerItem {
            createdAt = toKst(createdAt);
            updatedAt = toKst(updatedAt);
        }
    }

    /**
     * 그룹 질문. content는 status가 ready일 때만 있고, myAnswer·answers도 ready일 때만 채운다(아니면 null·빈 목록).
     */
    @Schema(name = "RoomQuestionView")
    public record QuestionView(
            Long questionId,
            Long roomId,
            @Schema(description = "이 질문의 날짜(KST). 그룹당 하루 1개")
            LocalDate questionDate,
            String content,
            @Schema(allowableValues = {"generating", "ready", "failed"},
                    description = "generating이면 1~3초 간격으로 GET current를 다시 요청. failed면 POST로 다시 생성할 수 있다")
            String status,
            OffsetDateTime createdAt,
            @Schema(description = "내 답. 없으면 null")
            AnswerItem myAnswer,
            @Schema(description = "그룹 멤버들의 답(내 답 포함), 작성순")
            List<AnswerItem> answers
    ) {
        public QuestionView {
            createdAt = toKst(createdAt);
        }

        public static QuestionView of(RoomQuestion question, String status, AnswerItem myAnswer,
                                      List<AnswerItem> answers) {
            return new QuestionView(question.getId(), question.getRoomId(), question.getQuestionDate(),
                    "ready".equals(status) ? question.getContent() : null, status, question.getCreatedAt(),
                    myAnswer, answers);
        }
    }

    /** 문서용: 409 ROOM_QUESTION_ALREADY_EXISTS 응답 본문(ApiResponse 형식). data에 이미 있는 오늘 질문이 담긴다. */
    @Schema(name = "RoomQuestionAlreadyExistsError")
    public record AlreadyExistsError(
            @Schema(example = "false") boolean success,
            @Schema(description = "이미 있는 오늘 질문(생성 중 포함)") QuestionView data,
            String message,
            @Schema(allowableValues = {"ROOM_QUESTION_ALREADY_EXISTS"}) String code
    ) {
    }

    /** 지난 질문 목록 항목. */
    @Schema(name = "RoomQuestionHistoryItem")
    public record HistoryItem(
            Long questionId,
            LocalDate questionDate,
            String content,
            @Schema(allowableValues = {"generating", "ready", "failed"})
            String status,
            OffsetDateTime createdAt,
            long answerCount
    ) {
        public HistoryItem {
            createdAt = toKst(createdAt);
        }
    }
}

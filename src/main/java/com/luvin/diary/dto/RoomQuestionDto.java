package com.luvin.diary.dto;

import com.luvin.diary.domain.RoomQuestion;
import com.luvin.user.domain.User;
import com.luvin.user.service.BreadNames;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

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

    /**
     * 답 하나. 작성자 정보 규칙은 DiaryFeedItem과 같다: authorNickname은 닉네임이 없으면 이름, authorBreadType은 빵 타입 id,
     * authorBreadName은 빵 이름(설문 전·탈퇴면 null). JPQL select new로 만들어지므로 필드 순서를 쿼리와 맞춘다.
     */
    @Schema(name = "RoomQuestionAnswerItem")
    public record AnswerItem(
            Long answerId,
            Long authorId,
            String authorNickname,
            String content,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            @Schema(description = "작성자 빵 타입 id(DiaryFeedItem.authorBreadType과 같은 값). 설문 전·탈퇴면 null")
            String authorBreadType,
            @Schema(description = "작성자 빵 이름(예: \"쫀쫀한 카스테라\"). 설문 전·탈퇴면 null")
            String authorBreadName
    ) {
        public AnswerItem {
            createdAt = toKst(createdAt);
            updatedAt = toKst(updatedAt);
        }

        /** JPQL select new용: 작성자의 최신 설문 결과 id로 빵 이름을 만든다. */
        public AnswerItem(Long answerId, Long authorId, String authorNickname, String content,
                          OffsetDateTime createdAt, OffsetDateTime updatedAt, String authorBreadType,
                          UUID authorSurveyResultId) {
            this(answerId, authorId, authorNickname, content, createdAt, updatedAt, authorBreadType,
                    BreadNames.of(authorSurveyResultId, authorBreadType));
        }
    }

    /**
     * 그룹 질문. content는 status가 ready일 때만 있고, myAnswer·answers도 ready일 때만 채운다(아니면 null·빈 목록).
     * creator는 생성 버튼을 누른 사람이다. 작성자 정보 규칙은 DiaryFeedItem과 같다: 닉네임은 계정 닉네임(없으면 이름),
     * 빵 타입은 id(예: "salt_bread", 설문 전이면 null). 탈퇴했으면 둘 다 null.
     */
    @Schema(name = "RoomQuestionView")
    public record QuestionView(
            Long questionId,
            Long roomId,
            @Schema(description = "이 질문 칸의 날짜(KST). slotStartAt의 날짜와 같다")
            LocalDate questionDate,
            @Schema(description = "이 질문의 칸 시작 시각(KST 정각, +09:00). 그룹당 칸마다 1개. 칸은 slotStartAt ~ +1시간",
                    example = "2026-11-02T09:00:00+09:00")
            OffsetDateTime slotStartAt,
            String content,
            @Schema(allowableValues = {"generating", "ready", "failed"},
                    description = "generating이면 1~3초 간격으로 GET current를 다시 요청. failed면 POST로 다시 생성할 수 있다")
            String status,
            OffsetDateTime createdAt,
            @Schema(description = "질문을 만든 사람의 닉네임(계정 닉네임, 없으면 이름). 탈퇴했으면 null")
            String creatorNickname,
            @Schema(description = "질문을 만든 사람의 빵 타입 id(DiaryFeedItem.authorBreadType과 같은 값). 설문 전·탈퇴면 null")
            String creatorBreadType,
            @Schema(description = "질문을 만든 사람의 id. 내가 만든 질문인지 알 때 쓴다")
            Long creatorId,
            @Schema(description = "질문을 만든 사람의 빵 이름(예: \"쫀쫀한 카스테라\"). 설문 전·탈퇴면 null")
            String creatorBreadName,
            @Schema(description = "내 답. 없으면 null")
            AnswerItem myAnswer,
            @Schema(description = "그룹 멤버들의 답(내 답 포함), 작성순")
            List<AnswerItem> answers
    ) {
        public QuestionView {
            slotStartAt = toKst(slotStartAt);
            createdAt = toKst(createdAt);
        }

        public static QuestionView of(RoomQuestion question, String status, User creator, AnswerItem myAnswer,
                                      List<AnswerItem> answers) {
            return new QuestionView(question.getId(), question.getRoomId(), question.getQuestionDate(),
                    question.getSlotStartAt(), "ready".equals(status) ? question.getContent() : null, status,
                    question.getCreatedAt(),
                    creator == null ? null : (creator.getNickname() != null ? creator.getNickname() : creator.getName()),
                    creator == null ? null : creator.getPersonalityType(),
                    question.getRequestedBy(), BreadNames.of(creator),
                    myAnswer, answers);
        }
    }

    /** 문서용: 409 ROOM_QUESTION_ALREADY_EXISTS 응답 본문(ApiResponse 형식). data에 이미 있는 이 칸의 질문이 담긴다. */
    @Schema(name = "RoomQuestionAlreadyExistsError")
    public record AlreadyExistsError(
            @Schema(example = "false") boolean success,
            @Schema(description = "이미 있는 이 칸의 질문(생성 중 포함)") QuestionView data,
            String message,
            @Schema(allowableValues = {"ROOM_QUESTION_ALREADY_EXISTS"}) String code
    ) {
    }

    /** 지난 질문 목록 항목. */
    @Schema(name = "RoomQuestionHistoryItem")
    public record HistoryItem(
            Long questionId,
            LocalDate questionDate,
            @Schema(description = "이 질문의 칸 시작 시각(KST 정각, +09:00)", example = "2026-11-02T09:00:00+09:00")
            OffsetDateTime slotStartAt,
            String content,
            @Schema(allowableValues = {"generating", "ready", "failed"})
            String status,
            OffsetDateTime createdAt,
            long answerCount
    ) {
        public HistoryItem {
            slotStartAt = toKst(slotStartAt);
            createdAt = toKst(createdAt);
        }
    }
}

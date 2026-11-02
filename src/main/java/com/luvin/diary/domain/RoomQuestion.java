package com.luvin.diary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 그룹(공유방) 질문. AI가 만들고, 그룹당 KST 정각 칸(예: 09:00~09:59)마다 1개다((room_id, slot_start_at) UNIQUE, Flyway V20).
 * 생성이 실패하면 FAILED로 남고, 같은 칸에서 다시 생성하면 같은 행을 GENERATING으로 되돌려 재시도한다.
 * 행은 RoomQuestionRepository의 native insert로 만든다(동시 요청에도 칸마다 1행).
 */
@Entity
@Table(name = "room_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /** 칸의 날짜(KST). slotStartAt에서 나온 값이고, 응답 questionDate 하위 호환용이다. */
    @Column(name = "question_date", nullable = false)
    private LocalDate questionDate;

    /** 이 질문의 칸 = 만든 시각을 KST 정각으로 내린 시각. */
    @Column(name = "slot_start_at", nullable = false)
    private OffsetDateTime slotStartAt;

    /** READY일 때만 있다. */
    @Column(name = "content", length = 300)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoomQuestionStatus status;

    /** 생성 버튼을 누른 사람(실패 후 다시 누르면 그 사람으로 바뀐다). AI 호출의 owner subject, 응답의 creator로 쓴다. */
    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

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
 * 그룹(공유방) 질문. AI가 만들고, 그룹당 하루(KST) 1개다((room_id, question_date) UNIQUE, Flyway V12).
 * 생성이 실패하면 FAILED로 남고, 같은 날 다시 생성하면 같은 행을 GENERATING으로 되돌려 재시도한다.
 * 행은 RoomQuestionRepository의 native insert로 만든다(동시 요청에도 하루 1행).
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

    @Column(name = "question_date", nullable = false)
    private LocalDate questionDate;

    /** READY일 때만 있다. */
    @Column(name = "content", length = 300)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoomQuestionStatus status;

    /** 생성 버튼을 누른 사람. AI 호출의 owner subject로 쓴다. */
    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

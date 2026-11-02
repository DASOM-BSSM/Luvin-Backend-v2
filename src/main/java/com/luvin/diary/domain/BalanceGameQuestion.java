package com.luvin.diary.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 오늘의 밸런스 게임 질문. 모든 사용자가 같은 날 같은 질문을 받는다(question_date UNIQUE, KST).
 * 테이블은 예전 daily_questions를 그대로 쓴다. question_date가 없는 예전 seed 질문은 밸런스 게임으로 나가지 않는다.
 * 행은 BalanceGameQuestionRepository의 native insert로 만든다(동시 요청에도 하루 1행).
 */
@Entity
@Table(name = "daily_questions",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_questions_question_date", columnNames = "question_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceGameQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_question_id")
    private Long id;

    @Column(name = "content", nullable = false, length = 300)
    private String content;

    /** 이 질문이 나가는 날(KST). 예전 seed 질문은 null. */
    @Column(name = "question_date")
    private LocalDate questionDate;

    /** PRESET(준비해 둔 목록). 예전 데이터는 AI·FALLBACK·null일 수 있다. */
    @Column(name = "source", length = 20)
    private String source;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BalanceGameOption> options = new ArrayList<>();
}

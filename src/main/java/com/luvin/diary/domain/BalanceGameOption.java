package com.luvin.diary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 밸런스 게임 보기(질문당 2개). 테이블은 예전 daily_question_options를 그대로 쓴다. */
@Entity
@Table(name = "daily_question_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceGameOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_question_id", nullable = false)
    private BalanceGameQuestion question;

    @Column(name = "content", nullable = false, length = 100)
    private String content;

    /** 예전 seed 보기의 성향 효과. 밸런스 게임 보기는 null. */
    @Column(name = "effects", length = 500)
    private String effects;

    public BalanceGameOption(BalanceGameQuestion question, String content) {
        this.question = question;
        this.content = content;
    }
}

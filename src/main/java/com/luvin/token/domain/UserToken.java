package com.luvin.token.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "user_tokens")
public class UserToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(name = "token_balance", nullable = false)
    private int tokenBalance;

    @Column(name = "free_simulation_count", nullable = false)
    private int freeSimulationCount;

    /** 식빵(유료 토큰) 잔액. tokenBalance는 소금빵(무료, 미니게임 보상)이다. 기존 행은 V23 마이그레이션이 0으로 채운다. */
    @ColumnDefault("0")
    @Column(name = "bread_token_balance", nullable = false)
    private int breadTokenBalance;

    protected UserToken() {
    }

    public UserToken(Long memberId, int tokenBalance, int freeSimulationCount) {
        this.memberId = memberId;
        this.tokenBalance = tokenBalance;
        this.freeSimulationCount = freeSimulationCount;
    }

    public void use(int amount) {
        this.tokenBalance -= amount;
    }

    public void earn(int amount) {
        this.tokenBalance += amount;
    }

    public void useBread(int amount) {
        this.breadTokenBalance -= amount;
    }

    public void earnBread(int amount) {
        this.breadTokenBalance += amount;
    }

    /** 이 종류 토큰의 잔액. */
    public int balanceOf(TokenKind kind) {
        return kind == TokenKind.BREAD ? breadTokenBalance : tokenBalance;
    }

    /** 이 종류 토큰을 차감한다. 잔액 확인은 호출부 책임이다. */
    public void use(TokenKind kind, int amount) {
        if (kind == TokenKind.BREAD) {
            useBread(amount);
        } else {
            use(amount);
        }
    }

    public void earn(TokenKind kind, int amount) {
        if (kind == TokenKind.BREAD) {
            earnBread(amount);
        } else {
            earn(amount);
        }
    }

    /** 무료 시즌 잔여 횟수를 1 소진한다. 0일 때는 호출하지 않는다(호출부 책임 — 토큰 차감으로 넘어가야 한다). */
    public void consumeFreeSimulation() {
        this.freeSimulationCount -= 1;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public int getTokenBalance() { return tokenBalance; }
    public int getFreeSimulationCount() { return freeSimulationCount; }
    public int getBreadTokenBalance() { return breadTokenBalance; }
}
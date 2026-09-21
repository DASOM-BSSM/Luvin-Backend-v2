package com.luvin.token.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public int getTokenBalance() { return tokenBalance; }
    public int getFreeSimulationCount() { return freeSimulationCount; }
}
package com.luvin.token.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "token_histories")
public class TokenHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenHistoryType type;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected TokenHistory() {
    }

    public TokenHistory(Long memberId, TokenHistoryType type, int amount, String reason) {
        this.memberId = memberId;
        this.type = type;
        this.amount = amount;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public Long getHistoryId() { return historyId; }
    public Long getMemberId() { return memberId; }
    public TokenHistoryType getType() { return type; }
    public int getAmount() { return amount; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
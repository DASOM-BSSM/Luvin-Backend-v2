package com.luvin.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 사용자 동작과 idempotency key의 안정적 연결 (요구사항 4절, 3.2).
 * businessKey는 동작을 유일하게 식별하는 문자열이다.
 * 예: "season:create", "season:{seasonId}:episode:1:generation", "season:{seasonId}:episode:3:selection".
 * 같은 businessKey로 재요청하면 기존 key를 그대로 재사용하고, timeout이 났다고 새 key를 발급하지 않는다.
 */
@Entity
@Table(name = "ai_idempotency_keys",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "action_type", "business_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiIdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private AiIdempotencyActionType actionType;

    @Column(name = "business_key", nullable = false, length = 200)
    private String businessKey;

    @Column(name = "idempotency_key", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AiIdempotencyKey(Long memberId, AiIdempotencyActionType actionType, String businessKey, UUID idempotencyKey) {
        this.memberId = memberId;
        this.actionType = actionType;
        this.businessKey = businessKey;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = LocalDateTime.now();
    }
}

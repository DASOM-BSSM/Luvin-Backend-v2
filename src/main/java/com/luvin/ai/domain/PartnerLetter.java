package com.luvin.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EP5(시즌 마무리) 완료 시 최종 매칭 상대가 보내는 쪽지. 시즌당 정확히 1개만 존재한다
 * (season_id unique) — "다시 굽기"로 상대가 바뀌어도 그건 report의 finalPartnerId 자체가
 * 바뀌는 것이고, 리포트를 먼저 보지 않고는 이 쪽지도 생성되지 않으므로 항상 최종 확정된
 * 상대 기준이다.
 *
 * 문구(message)는 AI가 실제로 생성한 리포트 narrative를 그대로 저장한다 — 새 AI 엔드포인트
 * 없이, 이미 있는 AI 생성 문구를 한 번 저장해서 매번 같은 값을 돌려주기 위함이다.
 */
@Entity
@Table(name = "partner_letters", uniqueConstraints = @UniqueConstraint(columnNames = {"season_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private AiSeason season;

    @Column(name = "partner_id", nullable = false, columnDefinition = "uuid")
    private UUID partnerId;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PartnerLetter(Long memberId, AiSeason season, UUID partnerId, String message) {
        this.memberId = memberId;
        this.season = season;
        this.partnerId = partnerId;
        this.message = message;
        this.read = false;
        this.createdAt = LocalDateTime.now();
    }

    public void markRead() {
        if (!this.read) {
            this.read = true;
            this.readAt = LocalDateTime.now();
        }
    }
}

package com.luvin.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 1화 사용자 선택 또는 3화 미니게임 결과. source_event_id는 Spring이 발급한 불변 UUID로,
 * 한 사용자 동작과 selection을 안정적으로 연결한다 (요구사항 4절, 5.4).
 * 최초 선택 후 수정하지 않는다 — 이 entity는 episode당 1건만 존재해야 한다.
 */
@Entity
@Table(name = "ai_selections",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"season_id", "episode_number"}),
                @UniqueConstraint(columnNames = {"source_event_id"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private AiSeason season;

    @Column(name = "episode_number", nullable = false)
    private Integer episodeNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private AiSelectionSource source;

    /** Spring이 한 사용자 선택/미니게임 결과 이벤트에 발급한 불변 UUID. */
    @Column(name = "source_event_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID sourceEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_result", length = 20)
    private AiGameResult gameResult;

    /** 성공(1화 선택 또는 3화 성공)일 때만 값이 있다. */
    @Column(name = "partner_id", columnDefinition = "uuid")
    private UUID partnerId;

    @Column(name = "ai_selection_id", columnDefinition = "uuid")
    private UUID aiSelectionId;

    @Column(name = "revision_after")
    private Integer revisionAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AiSelection(AiSeason season, Integer episodeNumber, AiSelectionSource source,
                        UUID sourceEventId, AiGameResult gameResult, UUID partnerId) {
        this.season = season;
        this.episodeNumber = episodeNumber;
        this.source = source;
        this.sourceEventId = sourceEventId;
        this.gameResult = gameResult;
        this.partnerId = partnerId;
        this.createdAt = LocalDateTime.now();
    }

    public void applyAiResult(UUID aiSelectionId, UUID selectedPartnerId, Integer revisionAfter) {
        this.aiSelectionId = aiSelectionId;
        this.partnerId = selectedPartnerId;
        this.revisionAfter = revisionAfter;
    }
}

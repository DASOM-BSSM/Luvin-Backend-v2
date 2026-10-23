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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 내 빵과 출연 빵 한 명 사이의 관계 기록(기억). 시즌마다 출연 빵(내 빵 제외)당 1개.
 * 목록 필드(recentMemories/knownFacts/openThreads)는 JSON 문자열 배열로 저장한다.
 */
@Entity
@Table(name = "ai_bread_relations", uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "target_bread_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiBreadRelation {

    public static final int MIN_AFFINITY = 0;
    public static final int MAX_AFFINITY = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private AiSeason season;

    @Column(name = "target_bread_id", nullable = false, columnDefinition = "uuid")
    private UUID targetBreadId;

    @Column(name = "affinity", nullable = false)
    private int affinity;

    /** 시즌 동안 이 빵에게 거절당한 횟수. */
    @Column(name = "reject_count", nullable = false)
    private int rejectCount;

    @Column(name = "status_text", length = 50)
    private String statusText;

    @Column(name = "memory_summary", columnDefinition = "text")
    private String memorySummary;

    /** 정리 단계가 준 선택 카드용 기억 한 줄(20자 이내). 회차 생성 응답이 기억을 주지 않을 때 쓴다. */
    @Column(name = "choice_memory", length = 40)
    private String choiceMemory;

    /** 최근 3회차 주요 장면. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recent_memories", nullable = false, columnDefinition = "jsonb")
    private String recentMemoriesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "known_facts", nullable = false, columnDefinition = "jsonb")
    private String knownFactsJson;

    /** 아직 안 풀린 떡밥·약속. [{"id","text"}] 형식(예전 문자열 배열 row도 읽을 때 변환한다). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "open_threads", nullable = false, columnDefinition = "jsonb")
    private String openThreadsJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AiBreadRelation(Long memberId, AiSeason season, UUID targetBreadId, int affinity, String statusText) {
        this.memberId = memberId;
        this.season = season;
        this.targetBreadId = targetBreadId;
        this.affinity = clamp(affinity);
        this.statusText = statusText;
        this.recentMemoriesJson = "[]";
        this.knownFactsJson = "[]";
        this.openThreadsJson = "[]";
        this.updatedAt = LocalDateTime.now();
    }

    public void addAffinity(int delta) {
        this.affinity = clamp(this.affinity + delta);
        this.updatedAt = LocalDateTime.now();
    }

    public void recordRejection() {
        this.rejectCount += 1;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStatusText(String statusText) {
        this.statusText = statusText;
        this.updatedAt = LocalDateTime.now();
    }

    /** 회차 완료 정리 단계 결과를 반영한다. null인 항목은 기존 값을 유지한다. */
    public void applyMemory(String memorySummary, String recentMemoriesJson, String knownFactsJson,
                            String openThreadsJson, String statusText, String choiceMemory) {
        if (memorySummary != null) this.memorySummary = memorySummary;
        if (choiceMemory != null) this.choiceMemory = choiceMemory;
        if (recentMemoriesJson != null) this.recentMemoriesJson = recentMemoriesJson;
        if (knownFactsJson != null) this.knownFactsJson = knownFactsJson;
        if (openThreadsJson != null) this.openThreadsJson = openThreadsJson;
        if (statusText != null) this.statusText = statusText;
        this.updatedAt = LocalDateTime.now();
    }

    private static int clamp(int value) {
        return Math.max(MIN_AFFINITY, Math.min(MAX_AFFINITY, value));
    }
}

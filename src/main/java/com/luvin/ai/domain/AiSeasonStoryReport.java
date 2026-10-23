package com.luvin.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 장면 블록 회차 시즌의 최종 리포트. 10화 완료 때 생성해 저장하고, GET /report가 이 값을 기존
 * 리포트 응답 형식(AiReportView) 그대로 돌려준다. 기존 5화 흐름 시즌의 리포트는 AI 서비스가 보관한다.
 */
@Entity
@Table(name = "ai_season_story_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSeasonStoryReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false, unique = true)
    private AiSeason season;

    /** AiReportView JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report", nullable = false, columnDefinition = "jsonb")
    private String reportJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AiSeasonStoryReport(AiSeason season, String reportJson) {
        this.season = season;
        this.reportJson = reportJson;
        this.createdAt = LocalDateTime.now();
    }
}

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
 * 2시즌부터 1화 생성 때 만든 새 시즌 설정(FastAPI season-design 결과)과, 그때 정한 이 시즌의 비교 자료(직전 시즌 하트
 * 대사·약점·강점, 이전 출연진 이름). 시즌당 하나이고 한 번 저장하면 바꾸지 않는다 — 이후 회차 본문과 시즌 리포트가 이
 * 값을 seasonContext로 쓴다. 다른 AI 테이블처럼 Hibernate ddl-auto가 만든다.
 */
@Entity
@Table(name = "ai_season_designs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSeasonDesign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false, unique = true)
    private AiSeason season;

    /** AiSeasonDesignRecord JSON. traits·designIntent·testsWeakness를 담고 있어 앱에 그대로 내보내지 않는다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "design", nullable = false, columnDefinition = "jsonb")
    private String designJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AiSeasonDesign(AiSeason season, String designJson) {
        this.season = season;
        this.designJson = designJson;
        this.createdAt = LocalDateTime.now();
    }
}

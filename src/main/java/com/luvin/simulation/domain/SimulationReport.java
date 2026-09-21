package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "simulation_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 1000)
    private String summary;

    @ElementCollection
    @CollectionTable(name = "simulation_report_strengths", joinColumns = @JoinColumn(name = "report_id"))
    @Column(name = "strength", length = 200)
    private List<String> strength;

    @ElementCollection
    @CollectionTable(name = "simulation_report_weaknesses", joinColumns = @JoinColumn(name = "report_id"))
    @Column(name = "weakness", length = 200)
    private List<String> weakness;

    @Column(nullable = false, length = 1000)
    private String advice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SimulationReport(Long memberId, String summary, List<String> strength, List<String> weakness, String advice) {
        this.memberId = memberId;
        this.summary = summary;
        this.strength = strength;
        this.weakness = weakness;
        this.advice = advice;
        this.createdAt = LocalDateTime.now();
    }
}

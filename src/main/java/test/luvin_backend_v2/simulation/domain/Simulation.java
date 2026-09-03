package com.luvin.simulation.domain;

import com.luvin.simulation.SimulationStatus;
import com.luvin.user.domain.User;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "simulations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Simulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clone_id")
    private AiClone aiClone;

    private String title;

    @Enumerated(EnumType.STRING)
    private SimulationStatus status;

    private int actionCount;
    private int interventionCount;
    private String currentSummary;
    private String reportSummary;
    private String matchingSummary;
    private String finalCoupleSummary;

    @Builder
    public Simulation(
            User user,
            AiClone aiClone,
            String title,
            SimulationStatus status,
            int actionCount,
            int interventionCount,
            String currentSummary,
            String reportSummary,
            String matchingSummary,
            String finalCoupleSummary
    ) {
        this.user = user;
        this.aiClone = aiClone;
        this.title = title;
        this.status = status;
        this.actionCount = actionCount;
        this.interventionCount = interventionCount;
        this.currentSummary = currentSummary;
        this.reportSummary = reportSummary;
        this.matchingSummary = matchingSummary;
        this.finalCoupleSummary = finalCoupleSummary;
    }

    public void incrementAction(String summary) {
        this.actionCount++;
        this.currentSummary = summary;
        this.status = SimulationStatus.RUNNING;
    }

    public void applyIntervention(String summary) {
        this.interventionCount++;
        this.currentSummary = summary;
        this.status = SimulationStatus.WAITING_USER_INPUT;
    }

    public void complete(String reportSummary, String matchingSummary, String finalCoupleSummary) {
        this.status = SimulationStatus.FINISHED;
        this.reportSummary = reportSummary;
        this.matchingSummary = matchingSummary;
        this.finalCoupleSummary = finalCoupleSummary;
    }
}
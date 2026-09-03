package com.luvin.simulation.domain;

import jakarta.persistence.Entity;
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
@Table(name = "simulation_actions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_id")
    private Simulation simulation;

    private int roundNumber;
    private String title;
    private String detail;

    @Builder
    public SimulationAction(Simulation simulation, int roundNumber, String title, String detail) {
        this.simulation = simulation;
        this.roundNumber = roundNumber;
        this.title = title;
        this.detail = detail;
    }
}
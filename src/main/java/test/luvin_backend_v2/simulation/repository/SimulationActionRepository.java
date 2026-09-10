package com.luvin.simulation.repository;

import com.luvin.simulation.domain.SimulationAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimulationActionRepository extends JpaRepository<SimulationAction, Long> {
    List<SimulationAction> findBySimulationIdOrderByRoundNumberAsc(Long simulationId);
}
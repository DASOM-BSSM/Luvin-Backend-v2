package com.luvin.simulation.repository;

import com.luvin.simulation.domain.SimulationIntervention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimulationInterventionRepository extends JpaRepository<SimulationIntervention, Long> {
    List<SimulationIntervention> findBySimulationIdOrderByIdAsc(Long simulationId);
}
package com.luvin.simulation.repository;

import com.luvin.simulation.domain.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulationRepository extends JpaRepository<Simulation, Long> {
    Optional<Simulation> findByIdAndUserId(Long id, Long userId);
}

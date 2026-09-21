package com.luvin.simulation.repository;

import com.luvin.simulation.domain.SimulationCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationCharacterRepository extends JpaRepository<SimulationCharacter, Long> {
}

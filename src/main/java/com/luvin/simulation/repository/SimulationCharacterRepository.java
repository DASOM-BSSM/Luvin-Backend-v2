package com.luvin.simulation.repository;

import com.luvin.simulation.domain.SimulationCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimulationCharacterRepository extends JpaRepository<SimulationCharacter, Long> {
    List<SimulationCharacter> findAllByGenderIgnoreCase(String gender);
}

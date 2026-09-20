package com.luvin.minigame.repository;

import com.luvin.minigame.domain.Minigame;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinigameRepository extends JpaRepository<Minigame, Long> {
}

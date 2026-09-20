package com.luvin.minigame.repository;

import com.luvin.minigame.domain.MinigamePlayResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MinigamePlayResultRepository extends JpaRepository<MinigamePlayResult, Long> {
    List<MinigamePlayResult> findAllByMemberIdOrderByPlayedAtDesc(Long memberId);
    List<MinigamePlayResult> findAllByMemberIdAndMinigame_GameIdOrderByPlayedAtDesc(Long memberId, Long gameId);
}

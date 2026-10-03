package com.luvin.simulation.repository;

import com.luvin.simulation.domain.AiClone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiCloneRepository extends JpaRepository<AiClone, Long> {
    Optional<AiClone> findTopByUserIdOrderByIdDesc(Long userId);

    /** 회원 탈퇴 시 user_id NOT NULL FK 제약 때문에 User 삭제 전에 먼저 지워야 한다. */
    void deleteByUserId(Long userId);
}

package com.luvin.analysis.repository;

import com.luvin.analysis.domain.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
    Optional<AnalysisResult> findTopByUserIdOrderByIdDesc(Long userId);

    /** 회원 탈퇴 시 user_id FK 제약 때문에 User 삭제 전에 먼저 지워야 한다. */
    void deleteByUserId(Long userId);
}
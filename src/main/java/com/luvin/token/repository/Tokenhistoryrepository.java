package com.luvin.token.repository;

import com.luvin.token.domain.TokenHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TokenHistoryRepository extends JpaRepository<TokenHistory, Long> {
    List<TokenHistory> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
}
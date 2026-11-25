package com.luvin.push.repository;

import com.luvin.push.domain.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    Optional<PushToken> findByToken(String token);

    List<PushToken> findAllByMemberIdIn(Collection<Long> memberIds);

    @Modifying
    @Query("DELETE FROM PushToken p WHERE p.memberId = :memberId AND p.token = :token")
    int deleteByMemberIdAndToken(@Param("memberId") Long memberId, @Param("token") String token);

    @Modifying
    @Query("DELETE FROM PushToken p WHERE p.token IN :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);
}

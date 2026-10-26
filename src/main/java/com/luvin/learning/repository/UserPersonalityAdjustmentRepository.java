package com.luvin.learning.repository;

import com.luvin.learning.domain.UserPersonalityAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPersonalityAdjustmentRepository extends JpaRepository<UserPersonalityAdjustment, Long> {
}

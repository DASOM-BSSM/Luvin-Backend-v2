package com.luvin.learning.repository;

import com.luvin.learning.domain.UserSpeechStyle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSpeechStyleRepository extends JpaRepository<UserSpeechStyle, Long> {
}

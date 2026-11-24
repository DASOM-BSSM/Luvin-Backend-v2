package com.luvin.ai.repository;

import com.luvin.ai.domain.AiStorySetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiStorySettingRepository extends JpaRepository<AiStorySetting, String> {
}

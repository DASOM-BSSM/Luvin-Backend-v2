package com.luvin.analysis.repository;

import com.luvin.analysis.domain.Content;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentRepository extends JpaRepository<Content, Long> {
    List<Content> findAllByTargetDatingStyle(String targetDatingStyle);
    List<Content> findAllByTargetDatingStyleIsNull();
}
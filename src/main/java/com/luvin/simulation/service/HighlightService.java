package com.luvin.simulation.service;

import com.luvin.simulation.dto.HighlightItem;
import com.luvin.simulation.dto.HighlightSaveRequest;

import java.util.List;

public interface HighlightService {
    List<HighlightItem> getHighlights(Long memberId);
    void saveHighlights(Long memberId, HighlightSaveRequest request);
}

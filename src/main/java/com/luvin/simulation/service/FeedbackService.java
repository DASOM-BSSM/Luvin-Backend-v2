package com.luvin.simulation.service;

import com.luvin.simulation.dto.FeedbackRequest;
import com.luvin.simulation.dto.FeedbackResponse;

public interface FeedbackService {
    FeedbackResponse saveFeedback(Long memberId, Long episodeId, FeedbackRequest request);
}

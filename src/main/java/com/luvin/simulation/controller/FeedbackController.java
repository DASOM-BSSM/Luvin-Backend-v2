package com.luvin.simulation.controller;

import com.luvin.common.security.SecurityUtils;
import com.luvin.simulation.dto.FeedbackRequest;
import com.luvin.simulation.dto.FeedbackResponse;
import com.luvin.simulation.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/episodes/{episodeId}/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public FeedbackResponse saveFeedback(@PathVariable Long episodeId, @Valid @RequestBody FeedbackRequest body) {
        return feedbackService.saveFeedback(SecurityUtils.getCurrentUserId(), episodeId, body);
    }
}

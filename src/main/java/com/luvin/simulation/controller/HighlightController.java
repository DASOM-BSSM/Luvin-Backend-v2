package com.luvin.simulation.controller;

import com.luvin.common.response.MessageResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.simulation.dto.HighlightItem;
import com.luvin.simulation.dto.HighlightSaveRequest;
import com.luvin.simulation.service.HighlightService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/simulation/highlights")
public class HighlightController {

    private final HighlightService highlightService;

    public HighlightController(HighlightService highlightService) {
        this.highlightService = highlightService;
    }

    @GetMapping
    public List<HighlightItem> getHighlights() {
        return highlightService.getHighlights(SecurityUtils.getCurrentUserId());
    }

    @PostMapping
    public MessageResponse saveHighlights(@RequestBody HighlightSaveRequest body) {
        highlightService.saveHighlights(SecurityUtils.getCurrentUserId(), body);
        return new MessageResponse("하이라이트 저장 완료");
    }
}

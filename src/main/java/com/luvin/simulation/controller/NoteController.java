package com.luvin.simulation.controller;

import com.luvin.common.security.SecurityUtils;
import com.luvin.simulation.dto.NoteResponse;
import com.luvin.simulation.service.NoteService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/episodes/{episodeId}/note")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public NoteResponse getNote(@PathVariable Long episodeId) {
        return noteService.getNote(SecurityUtils.getCurrentUserId(), episodeId);
    }
}

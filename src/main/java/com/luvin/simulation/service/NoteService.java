package com.luvin.simulation.service;

import com.luvin.simulation.dto.NoteResponse;

public interface NoteService {
    NoteResponse getNote(Long memberId, Long episodeId);
}

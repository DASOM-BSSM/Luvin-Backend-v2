package com.luvin.simulation.dto;

import com.luvin.simulation.domain.SimulationNote;

public class NoteResponse {
    private final Long noteId;
    private final String content;

    public NoteResponse(Long noteId, String content) {
        this.noteId = noteId;
        this.content = content;
    }

    public static NoteResponse from(SimulationNote note) {
        return new NoteResponse(note.getNoteId(), note.getContent());
    }

    public Long getNoteId() { return noteId; }
    public String getContent() { return content; }
}

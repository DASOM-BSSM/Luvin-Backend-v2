package com.luvin.simulation.dto;

import java.util.List;

public class HighlightSaveRequest {
    private List<HighlightItem> highlights;

    public HighlightSaveRequest() {}

    public List<HighlightItem> getHighlights() { return highlights; }
    public void setHighlights(List<HighlightItem> highlights) { this.highlights = highlights; }
}

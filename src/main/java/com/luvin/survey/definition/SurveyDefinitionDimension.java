package com.luvin.survey.definition;

/** 20개 성향 지표 하나. kind는 "core"(13개) 또는 "auxiliary"(7개). */
public record SurveyDefinitionDimension(String label, String kind) {

    public boolean isCore() {
        return "core".equals(kind);
    }
}

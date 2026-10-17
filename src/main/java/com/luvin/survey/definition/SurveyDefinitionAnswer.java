package com.luvin.survey.definition;

import java.util.Map;

public record SurveyDefinitionAnswer(String id, String text, Map<String, SurveyDefinitionEvidence> evidence) {
}

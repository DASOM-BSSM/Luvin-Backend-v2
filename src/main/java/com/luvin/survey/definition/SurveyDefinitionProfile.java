package com.luvin.survey.definition;

import java.util.Map;

public record SurveyDefinitionProfile(String id, String label, Map<String, SurveyDefinitionProfileFeature> features) {
}

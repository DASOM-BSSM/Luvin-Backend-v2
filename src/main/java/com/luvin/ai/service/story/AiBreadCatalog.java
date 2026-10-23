package com.luvin.ai.service.story;

import com.luvin.survey.definition.SurveyDefinitionConfig;
import com.luvin.survey.definition.SurveyDefinitionConfigLoader;
import com.luvin.survey.definition.SurveyDefinitionProfile;
import com.luvin.survey.definition.SurveyDefinitionV2SeedLoader;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 설문 설정(survey_config.json)에 있는 빵 종류 8종의 한글 이름과 성향 차원 이름. 출연진 이름("형용사 + 빵 종류")과
 * 내 빵 성향 요약(learnedProfile.summary)을 만들 때 쓴다. 설정은 기동할 때 한 번 읽는다.
 */
@Component
public class AiBreadCatalog {

    private final Map<String, String> breadLabels = new LinkedHashMap<>();
    private final Map<String, String> dimensionLabels = new LinkedHashMap<>();

    public AiBreadCatalog() {
        SurveyDefinitionConfig config = SurveyDefinitionConfigLoader.loadFromClasspath(
                SurveyDefinitionV2SeedLoader.RESOURCE_PATH);
        for (SurveyDefinitionProfile profile : config.profiles()) {
            breadLabels.put(profile.id(), profile.label());
        }
        config.dimensions().forEach((key, dimension) -> dimensionLabels.put(key, dimension.label()));
    }

    /** 빵 타입 id(예: salt_bread)의 한글 이름. 모르는 id면 null. */
    public String breadLabel(String breadTypeId) {
        return breadTypeId == null ? null : breadLabels.get(breadTypeId);
    }

    public List<String> allBreadLabels() {
        return List.copyOf(breadLabels.values());
    }

    /** 성향 차원 key(snake_case, 예: relationship_anxiety)의 한글 이름. */
    public String dimensionLabel(String dimensionKey) {
        return dimensionLabels.get(dimensionKey);
    }
}

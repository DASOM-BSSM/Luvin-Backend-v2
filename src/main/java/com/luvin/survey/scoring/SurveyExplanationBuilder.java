package com.luvin.survey.scoring;

import com.luvin.survey.definition.SurveyDefinitionConfig;
import com.luvin.survey.definition.SurveyDefinitionDimension;

import java.util.List;

/**
 * {@link ReasonEvidence}(구조화된 판정 근거)를 실제 사용자에게 보여줄 한국어 문구로 바꾼다.
 * 유형 자체는 {@link BreadTypeClassifier}가 이미 확정한 값이며, 이 클래스는 문구만 만든다 — LLM이나
 * 확률적 문구("87% 확률")를 넣지 않는다. 뚜렷한 근거가 없으면 제한된 일반 설명으로 대체한다.
 */
public final class SurveyExplanationBuilder {

    private SurveyExplanationBuilder() {
    }

    public static List<String> buildReasonTexts(List<ReasonEvidence> reasons,
                                                 SurveyDefinitionConfig config,
                                                 String primaryTypeLabel) {
        if (reasons.isEmpty()) {
            return List.of("여러 성향이 비슷하게 나타났으며, 현재 분류 기준에서는 " + primaryTypeLabel + "에 가장 가깝습니다.");
        }
        return reasons.stream()
                .map(reason -> toSentence(reason, config))
                .toList();
    }

    private static String toSentence(ReasonEvidence reason, SurveyDefinitionConfig config) {
        SurveyDefinitionDimension dimension = config.dimensions().get(reason.dimension());
        String label = dimension != null ? dimension.label() : reason.dimension();
        String directionPhrase = reason.direction() > 0 ? "높게" : "낮게";
        return label + " 성향이 다른 답변보다 " + directionPhrase + " 나타난 선택이 두드러졌어요.";
    }
}

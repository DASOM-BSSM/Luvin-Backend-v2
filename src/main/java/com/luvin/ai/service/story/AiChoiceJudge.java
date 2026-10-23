package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiAttachmentStyle;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import static com.luvin.ai.service.story.AiChoiceRules.ANXIOUS_OTHER_CHOSEN_PENALTY;
import static com.luvin.ai.service.story.AiChoiceRules.AVOIDANT_CONFESSION_PENALTY;
import static com.luvin.ai.service.story.AiChoiceRules.AVOIDANT_SHORT_POINT;
import static com.luvin.ai.service.story.AiChoiceRules.BASE_THRESHOLD;
import static com.luvin.ai.service.story.AiChoiceRules.CONFESSION_POINT;
import static com.luvin.ai.service.story.AiChoiceRules.MERCY_REJECT_COUNT;

/**
 * 선택 수락·거절 판정. AI에 맡기지 않는 순수 코드 규칙이다(같은 입력이면 항상 같은 결과).
 *
 * 순서: 혼자 있기 → 거절 2회 후 무조건 수락 → ②의 회피형(짧게 수락) → 기준 호감 + 성향 보정 비교.
 * 혼자 있기는 판정하지 않고 accepted=true로 둔다(공통 데이터 계약 3-2·5-3). 거절 사유도 없다.
 * 거절 사유는 보정이 없었다면 수락됐을 때만 성향 사유(anxious_hurt/avoidant_retreat)로 남긴다.
 */
public final class AiChoiceJudge {

    private AiChoiceJudge() {
    }

    /**
     * @param breadId            고른 빵. null이면 혼자 있기
     * @param previousChoiceBreadIds 이번 선택 이전에 내 빵이 고른 빵들(혼자 있기 제외)
     */
    public record Input(int pointId, UUID breadId, AiAttachmentStyle attachmentStyle, int affinity,
                        int rejectCount, Collection<UUID> previousChoiceBreadIds) {
    }

    /** shortReply는 회피형이 ②에서 수락할 때만 true — 결과 장면을 짧게 생성하라는 신호다. */
    public record Decision(boolean accepted, String reasonCode, boolean shortReply) {
    }

    public static Decision judge(Input input) {
        if (input.breadId() == null) {
            return new Decision(true, AiChoiceRules.REASON_ALONE, false);
        }
        Integer base = BASE_THRESHOLD.get(input.pointId());
        if (base == null) {
            throw new IllegalArgumentException("알 수 없는 선택 지점: " + input.pointId());
        }
        if (input.rejectCount() >= MERCY_REJECT_COUNT) {
            return new Decision(true, AiChoiceRules.REASON_MERCY, false);
        }
        AiAttachmentStyle style = input.attachmentStyle() == null ? AiAttachmentStyle.NONE : input.attachmentStyle();
        if (style == AiAttachmentStyle.AVOIDANT && input.pointId() == AVOIDANT_SHORT_POINT) {
            return new Decision(true, AiChoiceRules.REASON_AVOIDANT_SHORT, true);
        }

        int anxiousPenalty = style == AiAttachmentStyle.ANXIOUS && choseSomeoneElse(input)
                ? ANXIOUS_OTHER_CHOSEN_PENALTY : 0;
        int avoidantPenalty = style == AiAttachmentStyle.AVOIDANT && input.pointId() == CONFESSION_POINT
                ? AVOIDANT_CONFESSION_PENALTY : 0;
        int threshold = base + anxiousPenalty + avoidantPenalty;

        if (input.affinity() >= threshold) {
            return new Decision(true, AiChoiceRules.REASON_AFFINITY_MET, false);
        }
        if (input.affinity() >= base && anxiousPenalty > 0) {
            return new Decision(false, AiChoiceRules.REASON_ANXIOUS_HURT, false);
        }
        if (input.affinity() >= base && avoidantPenalty > 0) {
            return new Decision(false, AiChoiceRules.REASON_AVOIDANT_RETREAT, false);
        }
        return new Decision(false, AiChoiceRules.REASON_LOW_AFFINITY, false);
    }

    private static boolean choseSomeoneElse(Input input) {
        return input.previousChoiceBreadIds() != null && input.previousChoiceBreadIds().stream()
                .filter(Objects::nonNull)
                .anyMatch(id -> !id.equals(input.breadId()));
    }
}

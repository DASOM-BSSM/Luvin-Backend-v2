package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiAttachmentStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiChoiceJudgeTest {

    private static final UUID TARGET = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private static AiChoiceJudge.Decision judge(int point, AiAttachmentStyle style, int affinity, int rejectCount,
                                                List<UUID> previous) {
        return AiChoiceJudge.judge(new AiChoiceJudge.Input(point, TARGET, style, affinity, rejectCount, previous));
    }

    @ParameterizedTest(name = "지점 {0}: 기준 {1} 이상이면 수락, 미만이면 거절")
    @CsvSource({"1, 20", "2, 30", "3, 60", "4, 40"})
    void 선택_지점별_기준_호감(int point, int threshold) {
        AiChoiceJudge.Decision atThreshold = judge(point, AiAttachmentStyle.SECURE, threshold, 0, List.of());
        AiChoiceJudge.Decision below = judge(point, AiAttachmentStyle.SECURE, threshold - 1, 0, List.of());

        assertTrue(atThreshold.accepted());
        assertEquals(AiChoiceRules.REASON_AFFINITY_MET, atThreshold.reasonCode());
        assertFalse(below.accepted());
        assertEquals(AiChoiceRules.REASON_LOW_AFFINITY, below.reasonCode());
    }

    @Test
    void 혼자_있기는_판정하지_않고_accepted로_둔다() {
        AiChoiceJudge.Decision decision = AiChoiceJudge.judge(
                new AiChoiceJudge.Input(3, null, null, 0, 0, List.of(OTHER)));

        assertTrue(decision.accepted());
        assertEquals(AiChoiceRules.REASON_ALONE, decision.reasonCode());
        assertFalse(decision.shortReply());
    }

    @Test
    void 이_빵에게_2번_거절당했으면_호감과_상관없이_수락한다() {
        AiChoiceJudge.Decision decision = judge(3, AiAttachmentStyle.AVOIDANT, 0, 2, List.of(OTHER));

        assertTrue(decision.accepted());
        assertEquals(AiChoiceRules.REASON_MERCY, decision.reasonCode());
    }

    @Test
    void 거절_1번이면_아직_무조건_수락이_아니다() {
        assertFalse(judge(3, AiAttachmentStyle.SECURE, 0, 1, List.of()).accepted());
    }

    @Test
    void 불안형은_내_빵이_다른_빵을_고른_적이_있으면_기준이_15_오른다() {
        // ④ 기준 40 → 55. 50이면 보정 없이는 수락이지만 보정 때문에 거절된다.
        AiChoiceJudge.Decision hurt = judge(4, AiAttachmentStyle.ANXIOUS, 50, 0, List.of(OTHER));
        AiChoiceJudge.Decision enough = judge(4, AiAttachmentStyle.ANXIOUS, 55, 0, List.of(OTHER));

        assertFalse(hurt.accepted());
        assertEquals(AiChoiceRules.REASON_ANXIOUS_HURT, hurt.reasonCode());
        assertTrue(enough.accepted());
    }

    @Test
    void 불안형도_이전에_자기를_골랐거나_혼자였으면_보정이_없다() {
        assertTrue(judge(4, AiAttachmentStyle.ANXIOUS, 40, 0, List.of(TARGET)).accepted());
        assertTrue(judge(4, AiAttachmentStyle.ANXIOUS, 40, 0, List.of()).accepted());
    }

    @Test
    void 불안형이_기준에도_못_미치면_사유는_low_affinity다() {
        assertEquals(AiChoiceRules.REASON_LOW_AFFINITY,
                judge(4, AiAttachmentStyle.ANXIOUS, 39, 0, List.of(OTHER)).reasonCode());
    }

    @Test
    void 회피형은_마음을_전하는_3번에서_기준이_10_오른다() {
        AiChoiceJudge.Decision retreat = judge(3, AiAttachmentStyle.AVOIDANT, 65, 0, List.of());
        AiChoiceJudge.Decision enough = judge(3, AiAttachmentStyle.AVOIDANT, 70, 0, List.of());

        assertFalse(retreat.accepted());
        assertEquals(AiChoiceRules.REASON_AVOIDANT_RETREAT, retreat.reasonCode());
        assertTrue(enough.accepted());
    }

    @Test
    void 회피형은_2번에서_수락하되_짧게_생성하도록_short를_넘긴다() {
        AiChoiceJudge.Decision decision = judge(2, AiAttachmentStyle.AVOIDANT, 0, 0, List.of());

        assertTrue(decision.accepted());
        assertTrue(decision.shortReply());
        assertEquals(AiChoiceRules.REASON_AVOIDANT_SHORT, decision.reasonCode());
    }

    @Test
    void 회피형도_2번_외에는_short가_아니다() {
        assertFalse(judge(1, AiAttachmentStyle.AVOIDANT, 30, 0, List.of()).shortReply());
    }

    @Test
    void 안정형은_보정이_없다() {
        assertTrue(judge(3, AiAttachmentStyle.SECURE, 60, 0, List.of(OTHER)).accepted());
        assertTrue(judge(4, AiAttachmentStyle.SECURE, 40, 0, List.of(OTHER)).accepted());
    }
}

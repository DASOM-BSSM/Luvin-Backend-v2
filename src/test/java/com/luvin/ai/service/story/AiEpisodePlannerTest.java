package com.luvin.ai.service.story;

import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.domain.AiCastRole;
import com.luvin.ai.domain.AiSceneBlockType;
import com.luvin.ai.domain.AiSeasonSetting;
import com.luvin.ai.domain.AiStoryRoute;
import com.luvin.ai.service.story.AiEpisodePlan.BlockPlan;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiEpisodePlannerTest {

    private final AiStoryCastMember me = member(AiCastRole.MY_BREAD);
    private final AiStoryCastMember avoidant = member(AiCastRole.AVOIDANT);
    private final AiStoryCastMember anxious = member(AiCastRole.ANXIOUS);
    private final AiStoryCastMember secure = member(AiCastRole.SECURE);
    private final AiStoryCastMember friend = member(AiCastRole.FRIEND);
    private final AiStoryCastMember moodMaker = member(AiCastRole.MOOD_MAKER);
    private final List<AiStoryCastMember> cast = List.of(me, avoidant, anxious, secure, friend, moodMaker);

    private static AiStoryCastMember member(AiCastRole role) {
        return new AiStoryCastMember(UUID.randomUUID(), role, role.name(), "소금빵", null);
    }

    private AiEpisodePlan plan(int order, AiStoryRoute route, UUID partner,
                               Map<Integer, AiEpisodePlanner.ChoiceRecord> choices) {
        Map<UUID, Integer> affinity = new HashMap<>();
        cast.forEach(c -> affinity.put(c.breadId(), 25));
        affinity.put(secure.breadId(), 40);
        return AiEpisodePlanner.plan(new AiEpisodePlanner.Input(order, AiSeasonSetting.SCHOOL, route, partner, cast,
                affinity, Map.of(secure.breadId(), List.of(new StoryTypes.Thread("t1", "같이 떡볶이 먹기로 한 약속"))),
                choices));
    }

    private static List<AiSceneBlockType> types(AiEpisodePlan plan) {
        return plan.blocks().stream().map(BlockPlan::type).toList();
    }

    private static BlockPlan first(AiEpisodePlan plan, AiSceneBlockType type) {
        return plan.blocks().stream().filter(b -> b.type() == type).findFirst().orElseThrow();
    }

    @Test
    void 기본_블록_순서와_배경_장면() {
        AiEpisodePlan plan = plan(1, null, null, Map.of());

        assertEquals(List.of(AiSceneBlockType.GROUP, AiSceneBlockType.TRANSITION, AiSceneBlockType.PAIR,
                AiSceneBlockType.MESSENGER), types(plan));
        assertEquals("무리에 처음 합류", plan.commonEvent());
        assertEquals("새 학기 반 배정", plan.settingScene());
        // 메신저 방 이름은 Spring이 정한다: 1:1은 상대 이름, 2화 단톡은 배경별 방 이름.
        assertEquals(secure.name(), first(plan, AiSceneBlockType.MESSENGER).roomName());
        assertEquals("우리 반 단톡", first(plan(2, null, null, Map.of()), AiSceneBlockType.MESSENGER).roomName());
        assertEquals(6, first(plan, AiSceneBlockType.GROUP).participantIds().size());
    }

    @Test
    void 선택_회차는_마지막에_choice_블록이_붙고_혼자_있기는_후보에_없다() {
        AiEpisodePlan plan = plan(3, null, null, Map.of());

        assertEquals(AiSceneBlockType.CHOICE, plan.blocks().get(plan.blocks().size() - 1).type());
        BlockPlan choice = first(plan, AiSceneBlockType.CHOICE);
        assertEquals(1, choice.choice().pointId());
        assertEquals(List.of(avoidant.breadId(), anxious.breadId(), secure.breadId()), choice.choice().candidateIds());
    }

    @Test
    void 기본_pair_상대는_호감이_가장_높은_연애_후보다() {
        assertEquals(secure.breadId(), first(plan(5, null, null, Map.of()), AiSceneBlockType.PAIR).focusBreadId());
    }

    @Test
    void 사화_pair_상대는_1번_선택에서_수락한_빵_혼자_있기면_친구() {
        AiEpisodePlan accepted = plan(4, null, null, Map.of(1, new AiEpisodePlanner.ChoiceRecord(anxious.breadId(), true)));
        AiEpisodePlan alone = plan(4, null, null, Map.of(1, new AiEpisodePlanner.ChoiceRecord(null, false)));

        assertEquals(anxious.breadId(), first(accepted, AiSceneBlockType.PAIR).focusBreadId());
        assertEquals(friend.breadId(), first(alone, AiSceneBlockType.PAIR).focusBreadId());
    }

    @Test
    void 육화는_2번_선택에서_혼자_있기면_아무에게도_연락하지_않는_장면() {
        AiEpisodePlan alone = plan(6, null, null, Map.of(2, new AiEpisodePlanner.ChoiceRecord(null, false)));

        BlockPlan pair = first(alone, AiSceneBlockType.PAIR);
        assertNull(pair.focusBreadId());
        assertEquals(List.of(me.breadId()), pair.participantIds());
        assertEquals("group", first(alone, AiSceneBlockType.MESSENGER).messengerKind());
        assertEquals(3, first(alone, AiSceneBlockType.CHOICE).choice().pointId());
    }

    @Test
    void 칠화부터는_route에_따라_pair_상대가_연인_또는_친구() {
        AiEpisodePlan romance = plan(7, AiStoryRoute.ROMANCE, avoidant.breadId(), Map.of());
        AiEpisodePlan solo = plan(7, AiStoryRoute.SOLO, null, Map.of());

        assertEquals(avoidant.breadId(), first(romance, AiSceneBlockType.PAIR).focusBreadId());
        assertEquals(AiStoryRoute.ROMANCE, romance.route());
        assertEquals(friend.breadId(), first(solo, AiSceneBlockType.PAIR).focusBreadId());
        assertEquals(AiStoryRoute.SOLO, solo.route());
        assertEquals(romance.commonEvent(), solo.commonEvent());
    }

    @Test
    void 팔화_4번_선택_후보는_다툰_상대와_친구() {
        AiEpisodePlan romance = plan(8, AiStoryRoute.ROMANCE, avoidant.breadId(), Map.of());
        AiEpisodePlan solo = plan(8, AiStoryRoute.SOLO, null, Map.of());

        assertEquals(List.of(avoidant.breadId(), friend.breadId()),
                first(romance, AiSceneBlockType.CHOICE).choice().candidateIds());
        assertEquals(List.of(secure.breadId(), friend.breadId()),
                first(solo, AiSceneBlockType.CHOICE).choice().candidateIds());
    }

    @Test
    void 구화는_4번_결과_장면으로_시작한다() {
        AiEpisodePlan withPartner = plan(9, AiStoryRoute.SOLO, null,
                Map.of(4, new AiEpisodePlanner.ChoiceRecord(secure.breadId(), true)));
        AiEpisodePlan alone = plan(9, AiStoryRoute.SOLO, null, Map.of(4, new AiEpisodePlanner.ChoiceRecord(null, false)));

        assertEquals(AiSceneBlockType.PAIR, withPartner.blocks().get(0).type());
        assertEquals(secure.breadId(), withPartner.blocks().get(0).focusBreadId());
        assertEquals(List.of(me.breadId()), alone.blocks().get(0).participantIds());
    }

    @Test
    void 십화는_인터뷰룸이_출연_빵_수만큼이고_선택이_없다() {
        AiEpisodePlan plan = plan(10, AiStoryRoute.SOLO, null, Map.of());

        assertEquals(List.of(AiSceneBlockType.GROUP, AiSceneBlockType.TRANSITION, AiSceneBlockType.PAIR,
                AiSceneBlockType.INTERVIEW, AiSceneBlockType.INTERVIEW, AiSceneBlockType.INTERVIEW,
                AiSceneBlockType.INTERVIEW, AiSceneBlockType.INTERVIEW), types(plan));
        assertEquals(5, plan.interviewBlocks().size());
        assertEquals(3, plan.episodeBlocks().size());
    }

    @Test
    void pair_상대의_열린_떡밥을_다룬다() {
        assertEquals(List.of(new StoryTypes.Thread("t1", "같이 떡볶이 먹기로 한 약속")),
                plan(5, null, null, Map.of()).openThreads());
    }

    @Test
    void 선택_반응_장면은_고른_빵과_내_빵이고_혼자_있기면_내_빵만() {
        AiChoiceJudge.Decision shortAccept = new AiChoiceJudge.Decision(true, AiChoiceRules.REASON_AVOIDANT_SHORT, true);
        AiChoiceJudge.Decision alone = new AiChoiceJudge.Decision(false, AiChoiceRules.REASON_ALONE, false);

        BlockPlan reaction = AiEpisodePlanner.planChoiceReaction(2, avoidant.breadId(), shortAccept, cast);
        BlockPlan aloneReaction = AiEpisodePlanner.planChoiceReaction(1, null, alone, cast);

        assertEquals(AiSceneBlockType.CHOICE_RESULT, reaction.type());
        assertEquals(List.of(me.breadId(), avoidant.breadId()), reaction.participantIds());
        assertTrue(reaction.shortReply());
        assertEquals(List.of(me.breadId()), aloneReaction.participantIds());
    }
}

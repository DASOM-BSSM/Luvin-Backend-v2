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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiEpisodePlannerTest {

    // 만나는 순서: 안정형(1) → 회피형(2) → 불안형(3)
    private final AiStoryCastMember me = member(AiCastRole.MY_BREAD, null);
    private final AiStoryCastMember avoidant = member(AiCastRole.AVOIDANT, 2);
    private final AiStoryCastMember anxious = member(AiCastRole.ANXIOUS, 3);
    private final AiStoryCastMember secure = member(AiCastRole.SECURE, 1);
    private final AiStoryCastMember friend = member(AiCastRole.FRIEND, null);
    private final AiStoryCastMember moodMaker = member(AiCastRole.MOOD_MAKER, null);
    private final List<AiStoryCastMember> cast = List.of(me, avoidant, anxious, secure, friend, moodMaker);

    private static AiStoryCastMember member(AiCastRole role, Integer candidateOrder) {
        return new AiStoryCastMember(UUID.randomUUID(), role, role.name(), "소금빵", null, candidateOrder);
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
        assertEquals(6, first(plan, AiSceneBlockType.GROUP).participantIds().size());
        assertEquals(List.of("b1", "b2", "b3", "b4"), plan.blocks().stream().map(BlockPlan::id).toList());
    }

    @Test
    void 단톡방은_2화_transition_앞에만_개인톡은_1_3_4_6_7_9화와_5화에만() {
        // 2화: group → 단톡방(배경별 방 이름) → transition → pair
        AiEpisodePlan ep2 = plan(2, null, null, Map.of());
        assertEquals(List.of(AiSceneBlockType.GROUP, AiSceneBlockType.MESSENGER, AiSceneBlockType.TRANSITION,
                AiSceneBlockType.PAIR), types(ep2));
        assertEquals("group", first(ep2, AiSceneBlockType.MESSENGER).messengerKind());
        assertEquals("2학년 2반 단톡방ㅎㅎ", first(ep2, AiSceneBlockType.MESSENGER).roomName());

        // 1·3·4·6·7·9화: 첫 pair 뒤에 그 회차 1:1 상대와 개인톡(방 이름은 상대 이름)
        AiEpisodePlan ep4 = plan(4, null, null, Map.of(1, new AiEpisodePlanner.ChoiceRecord(anxious.breadId(), true)));
        BlockPlan direct = first(ep4, AiSceneBlockType.MESSENGER);
        assertEquals("direct", direct.messengerKind());
        assertEquals(anxious.breadId(), direct.focusBreadId());
        assertEquals(anxious.name(), direct.roomName());
        assertEquals(secure.breadId(), first(plan(1, null, null, Map.of()), AiSceneBlockType.MESSENGER).focusBreadId());
        assertEquals(anxious.breadId(), first(plan(3, null, null, Map.of()), AiSceneBlockType.MESSENGER).focusBreadId());
        assertEquals(friend.breadId(), first(plan(7, AiStoryRoute.SOLO, null, Map.of()), AiSceneBlockType.MESSENGER)
                .focusBreadId());

        // 8·10화와 혼자인 9화에는 메신저가 없다.
        for (AiEpisodePlan noMessenger : List.of(plan(8, AiStoryRoute.SOLO, null, Map.of()),
                plan(10, AiStoryRoute.SOLO, null, Map.of()),
                plan(9, AiStoryRoute.SOLO, null, Map.of(4, new AiEpisodePlanner.ChoiceRecord(null, true))))) {
            assertTrue(types(noMessenger).stream().noneMatch(t -> t == AiSceneBlockType.MESSENGER),
                    noMessenger.order() + "화");
        }
    }

    @Test
    void 모든_회차에서_transition은_첫_pair_바로_앞이고_첫_pair_뒤에_group이나_단톡방이_없다() {
        Map<Integer, AiEpisodePlanner.ChoiceRecord> choices = Map.of(
                1, new AiEpisodePlanner.ChoiceRecord(secure.breadId(), true),
                2, new AiEpisodePlanner.ChoiceRecord(secure.breadId(), true),
                4, new AiEpisodePlanner.ChoiceRecord(secure.breadId(), true));
        for (int order = 1; order <= 10; order++) {
            AiEpisodePlan plan = plan(order, order >= 7 ? AiStoryRoute.ROMANCE : null,
                    order >= 7 ? secure.breadId() : null, choices);
            List<AiSceneBlockType> types = types(plan);
            int firstPair = types.indexOf(AiSceneBlockType.PAIR);
            assertEquals(AiSceneBlockType.TRANSITION, types.get(firstPair - 1), order + "화");
            assertEquals(1, types.stream().filter(t -> t == AiSceneBlockType.TRANSITION).count(), order + "화");
            for (BlockPlan after : plan.blocks().subList(firstPair + 1, plan.blocks().size())) {
                assertFalse(after.type() == AiSceneBlockType.GROUP, order + "화");
                assertFalse(after.type() == AiSceneBlockType.MESSENGER && "group".equals(after.messengerKind()),
                        order + "화");
            }
        }
    }

    @Test
    void 선택_회차는_마지막에_choice_블록이_붙고_혼자_있기는_후보에_없다() {
        AiEpisodePlan plan = plan(3, null, null, Map.of());

        assertEquals(AiSceneBlockType.CHOICE, plan.blocks().get(plan.blocks().size() - 1).type());
        BlockPlan choice = first(plan, AiSceneBlockType.CHOICE);
        assertEquals(1, choice.choice().pointId());
        assertEquals(List.of(secure.breadId(), avoidant.breadId(), anxious.breadId()), choice.choice().candidateIds());
    }

    @Test
    void 일화부터_삼화는_후보를_만나는_순서대로_일대일_상대가_된다() {
        assertEquals(secure.breadId(), first(plan(1, null, null, Map.of()), AiSceneBlockType.PAIR).focusBreadId());
        assertEquals(avoidant.breadId(), first(plan(2, null, null, Map.of()), AiSceneBlockType.PAIR).focusBreadId());
        assertEquals(anxious.breadId(), first(plan(3, null, null, Map.of()), AiSceneBlockType.PAIR).focusBreadId());
    }

    @Test
    void 오화는_친구와_일대일이고_후보_셋이_각자_메신저로_연락한다() {
        AiEpisodePlan plan = plan(5, null, null, Map.of());

        assertEquals(friend.breadId(), first(plan, AiSceneBlockType.PAIR).focusBreadId());
        List<BlockPlan> messengers = plan.blocks().stream()
                .filter(b -> b.type() == AiSceneBlockType.MESSENGER).toList();
        assertEquals(List.of(secure.breadId(), avoidant.breadId(), anxious.breadId()),
                messengers.stream().map(BlockPlan::focusBreadId).toList());
        assertTrue(messengers.stream().allMatch(m -> "direct".equals(m.messengerKind())));
        assertEquals(AiSceneBlockType.CHOICE, plan.blocks().get(plan.blocks().size() - 1).type());
    }

    @Test
    void 사화_pair_상대는_1번_선택에서_수락한_빵_혼자_있기면_친구() {
        AiEpisodePlan accepted = plan(4, null, null, Map.of(1, new AiEpisodePlanner.ChoiceRecord(anxious.breadId(), true)));
        AiEpisodePlan alone = plan(4, null, null, Map.of(1, new AiEpisodePlanner.ChoiceRecord(null, false)));

        assertEquals(anxious.breadId(), first(accepted, AiSceneBlockType.PAIR).focusBreadId());
        assertEquals(friend.breadId(), first(alone, AiSceneBlockType.PAIR).focusBreadId());
    }

    @Test
    void 육화는_2번_선택의_빵이고_혼자_있기면_친구() {
        AiEpisodePlan accepted = plan(6, null, null, Map.of(2, new AiEpisodePlanner.ChoiceRecord(anxious.breadId(), true)));
        AiEpisodePlan alone = plan(6, null, null, Map.of(2, new AiEpisodePlanner.ChoiceRecord(null, true)));

        assertEquals(anxious.breadId(), first(accepted, AiSceneBlockType.PAIR).focusBreadId());
        assertEquals(friend.breadId(), first(alone, AiSceneBlockType.PAIR).focusBreadId());
        assertEquals(3, first(alone, AiSceneBlockType.CHOICE).choice().pointId());
    }

    @Test
    void 팔화_혼자_흐름은_호감이_가장_높은_후보와_일대일() {
        assertEquals(secure.breadId(), first(plan(8, AiStoryRoute.SOLO, null, Map.of()), AiSceneBlockType.PAIR)
                .focusBreadId());
        assertEquals(avoidant.breadId(), first(plan(8, AiStoryRoute.ROMANCE, avoidant.breadId(), Map.of()),
                AiSceneBlockType.PAIR).focusBreadId());
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
    void 팔화_4번_선택_후보는_출연_빵_5명이고_흐름과_상관없이_같은_순서다() {
        List<UUID> expected = List.of(secure.breadId(), avoidant.breadId(), anxious.breadId(), friend.breadId(),
                moodMaker.breadId());
        AiEpisodePlan romance = plan(8, AiStoryRoute.ROMANCE, avoidant.breadId(), Map.of());
        AiEpisodePlan solo = plan(8, AiStoryRoute.SOLO, null, Map.of());

        assertEquals(expected, first(romance, AiSceneBlockType.CHOICE).choice().candidateIds());
        assertEquals(expected, first(solo, AiSceneBlockType.CHOICE).choice().candidateIds());
        assertFalse(expected.contains(me.breadId()));
    }

    @Test
    void 구화_1대1_상대는_4번_결과() {
        AiEpisodePlan withPartner = plan(9, AiStoryRoute.SOLO, null,
                Map.of(4, new AiEpisodePlanner.ChoiceRecord(secure.breadId(), true)));
        AiEpisodePlan alone = plan(9, AiStoryRoute.SOLO, null, Map.of(4, new AiEpisodePlanner.ChoiceRecord(null, false)));

        assertEquals(secure.breadId(), first(withPartner, AiSceneBlockType.PAIR).focusBreadId());
        assertEquals(List.of(me.breadId()), first(alone, AiSceneBlockType.PAIR).participantIds());
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
                plan(1, null, null, Map.of()).openThreads());
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

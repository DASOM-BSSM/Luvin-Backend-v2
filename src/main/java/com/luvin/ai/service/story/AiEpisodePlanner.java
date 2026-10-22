package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiCastRole;
import com.luvin.ai.domain.AiSceneBlockType;
import com.luvin.ai.domain.AiSeasonSetting;
import com.luvin.ai.domain.AiStoryRoute;
import com.luvin.ai.service.story.AiEpisodePlan.BlockPlan;
import com.luvin.ai.service.story.AiEpisodePlan.ChoicePlan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 설계 단계: 회차 공통 사건·배경 장면, 블록 순서, 블록별 참여 빵, pair 상대, 다룰 열린 떡밥을 코드로 정한다.
 *
 * 기본 순서는 group → transition → pair → messenger (→ 3·5·6·8화 choice). 9화는 ④ 결과 장면(pair)으로 시작하고,
 * 10화는 group → transition → pair → interview × 출연 빵 수다.
 *
 * pair 상대:
 * - 4화 = ①에서 수락한 빵, 아니면 친구 빵 / 6화 = ②에서 수락한 빵, 아니면 혼자 / 9화 = ④에서 수락한 빵, 아니면 혼자
 * - 7·8·10화 = romance면 연인, solo면 친구 빵
 * - 그 외(1·2·3·5화) = 연애 후보 중 호감이 가장 높은 빵(같으면 회차마다 돌아가며)
 */
public final class AiEpisodePlanner {

    private static final int OPEN_THREAD_LIMIT = 2;
    private static final String MESSENGER_DIRECT = "direct";
    private static final String MESSENGER_GROUP = "group";

    private AiEpisodePlanner() {
    }

    /**
     * @param choicesByPoint 이미 기록된 선택(지점 → 기록)
     * @param affinity       출연 빵별 현재 호감
     * @param openThreads    출연 빵별 아직 안 풀린 떡밥
     */
    public record Input(int order, AiSeasonSetting setting, AiStoryRoute route, UUID romancePartnerId,
                        List<AiStoryCastMember> cast, Map<UUID, Integer> affinity,
                        Map<UUID, List<String>> openThreads, Map<Integer, ChoiceRecord> choicesByPoint) {
    }

    public record ChoiceRecord(UUID breadId, boolean accepted) {
    }

    public static AiEpisodePlan plan(Input input) {
        int order = input.order();
        AiStoryCastMember me = member(input.cast(), AiCastRole.MY_BREAD);
        UUID partner = pairPartner(input);
        List<UUID> everyone = input.cast().stream().map(AiStoryCastMember::breadId).toList();
        List<UUID> pairParticipants = partner == null ? List.of(me.breadId()) : List.of(me.breadId(), partner);

        List<BlockPlan> blocks = new ArrayList<>();
        if (order == 9) {
            blocks.add(block("b1", AiSceneBlockType.PAIR, pairParticipants, null, partner));
            blocks.add(block("b2", AiSceneBlockType.TRANSITION, List.of(), null, null));
            blocks.add(block("b3", AiSceneBlockType.GROUP, everyone, null, null));
            blocks.add(messenger("b4", order, me, partner, everyone));
        } else {
            blocks.add(block("b1", AiSceneBlockType.GROUP, everyone, null, null));
            blocks.add(block("b2", AiSceneBlockType.TRANSITION, List.of(), null, null));
            blocks.add(block("b3", AiSceneBlockType.PAIR, pairParticipants, null, partner));
            if (order == AiStoryCatalog.FINAL_ORDER) {
                int index = 4;
                for (AiStoryCastMember member : input.cast()) {
                    if (!member.isMe()) {
                        blocks.add(block("b" + index++, AiSceneBlockType.INTERVIEW,
                                List.of(member.breadId()), null, member.breadId()));
                    }
                }
            } else {
                blocks.add(messenger("b4", order, me, partner, everyone));
            }
        }

        Integer pointId = AiStoryCatalog.pointOf(order);
        if (pointId != null) {
            ChoicePlan choice = new ChoicePlan(pointId, AiStoryCatalog.question(pointId), candidates(input, pointId));
            blocks.add(new BlockPlan("b" + (blocks.size() + 1), AiSceneBlockType.CHOICE, List.of(), null, choice, null, false));
        }

        return new AiEpisodePlan(order, input.setting(), AiStoryCatalog.commonEvent(order),
                AiStoryCatalog.settingScene(input.setting(), order), order >= 7 ? input.route() : null,
                AiStoryCatalog.legacyReferenceEpisode(order), openThreads(input, partner), List.copyOf(blocks));
    }

    /**
     * 선택 직후 보여줄 결과 장면. 수락이면 고른 빵과의 장면 하나, 거절이면 반응 장면 뒤에 대체 장면(②는 내 빵 혼자,
     * 나머지는 친구 빵)을 붙인다. 혼자 있기면 내 빵 혼자인 장면 하나다.
     */
    public static List<BlockPlan> planChoiceResult(int pointId, UUID breadId, AiChoiceJudge.Decision decision,
                                                   List<AiStoryCastMember> cast) {
        AiStoryCastMember me = member(cast, AiCastRole.MY_BREAD);
        String prefix = "c" + pointId + "-";
        List<UUID> reactionParticipants = breadId == null ? List.of(me.breadId()) : List.of(me.breadId(), breadId);
        List<BlockPlan> blocks = new ArrayList<>();
        blocks.add(new BlockPlan(prefix + "1", AiSceneBlockType.CHOICE_RESULT, reactionParticipants, null, null,
                breadId, decision.shortReply()));
        if (breadId != null && !decision.accepted()) {
            UUID alternative = pointId == 2 ? null : member(cast, AiCastRole.FRIEND).breadId();
            List<UUID> alternativeParticipants = alternative == null
                    ? List.of(me.breadId()) : List.of(me.breadId(), alternative);
            blocks.add(block(prefix + "2", AiSceneBlockType.TRANSITION, List.of(), null, null));
            blocks.add(block(prefix + "3", AiSceneBlockType.PAIR, alternativeParticipants, null, alternative));
        }
        return List.copyOf(blocks);
    }

    private static UUID pairPartner(Input input) {
        UUID friend = member(input.cast(), AiCastRole.FRIEND).breadId();
        return switch (input.order()) {
            case 4 -> acceptedBreadOr(input.choicesByPoint().get(1), friend);
            case 6 -> acceptedBreadOr(input.choicesByPoint().get(2), null);
            case 9 -> acceptedBreadOr(input.choicesByPoint().get(4), null);
            case 7, 8, 10 -> input.route() == AiStoryRoute.ROMANCE && input.romancePartnerId() != null
                    ? input.romancePartnerId() : friend;
            default -> favoriteCandidate(input);
        };
    }

    private static UUID acceptedBreadOr(ChoiceRecord choice, UUID fallback) {
        if (choice != null && choice.breadId() != null && choice.accepted()) {
            return choice.breadId();
        }
        return fallback;
    }

    /** ①~③은 연애 후보 3명, ④는 다툰 상대(8화 pair 상대)와 친구 빵. */
    private static List<UUID> candidates(Input input, int pointId) {
        if (pointId == 4) {
            UUID friend = member(input.cast(), AiCastRole.FRIEND).breadId();
            UUID conflictPartner = pairPartner(input);
            if (conflictPartner == null || conflictPartner.equals(friend)) {
                conflictPartner = favoriteCandidate(input);
            }
            return List.of(conflictPartner, friend);
        }
        return romanceCandidates(input.cast()).stream().map(AiStoryCastMember::breadId).toList();
    }

    private static UUID favoriteCandidate(Input input) {
        List<AiStoryCastMember> candidates = romanceCandidates(input.cast());
        int rotation = input.order() % candidates.size();
        return candidates.stream()
                .max(Comparator.<AiStoryCastMember>comparingInt(c -> input.affinity().getOrDefault(c.breadId(), 0))
                        .thenComparingInt(c -> -Math.floorMod(candidates.indexOf(c) - rotation, candidates.size())))
                .map(AiStoryCastMember::breadId)
                .orElseThrow();
    }

    private static List<AiStoryCastMember> romanceCandidates(List<AiStoryCastMember> cast) {
        return cast.stream().filter(c -> c.castRole().isRomanceCandidate())
                .sorted(Comparator.comparing(AiStoryCastMember::castRole))
                .toList();
    }

    /** pair 상대의 떡밥을 먼저, 모자라면 다른 빵의 떡밥으로 채운다. */
    private static List<String> openThreads(Input input, UUID partner) {
        List<String> picked = new ArrayList<>();
        if (partner != null) {
            picked.addAll(input.openThreads().getOrDefault(partner, List.of()));
        }
        input.cast().stream()
                .filter(c -> !c.isMe() && !c.breadId().equals(partner))
                .forEach(c -> picked.addAll(input.openThreads().getOrDefault(c.breadId(), List.of())));
        return picked.stream().distinct().limit(OPEN_THREAD_LIMIT).toList();
    }

    /** 2화는 단톡방이 생기는 회차라 그룹 메신저, 혼자인 회차도 그룹 메신저, 그 외에는 pair 상대와 1:1. */
    private static BlockPlan messenger(String id, int order, AiStoryCastMember me, UUID partner, List<UUID> everyone) {
        if (order == 2 || partner == null) {
            return block(id, AiSceneBlockType.MESSENGER, everyone, MESSENGER_GROUP, null);
        }
        return block(id, AiSceneBlockType.MESSENGER, List.of(me.breadId(), partner), MESSENGER_DIRECT, partner);
    }

    private static BlockPlan block(String id, AiSceneBlockType type, List<UUID> participants, String messengerKind,
                                   UUID focus) {
        return new BlockPlan(id, type, participants, messengerKind, null, focus, false);
    }

    private static AiStoryCastMember member(List<AiStoryCastMember> cast, AiCastRole role) {
        return cast.stream().filter(c -> c.castRole() == role).findFirst()
                .orElseThrow(() -> new IllegalStateException("출연진에 " + role + "이(가) 없습니다."));
    }
}

package com.luvin.ai.service.story;

import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.domain.AiCastRole;
import com.luvin.ai.domain.AiSceneBlockType;
import com.luvin.ai.domain.AiSeasonSetting;
import com.luvin.ai.domain.AiStoryRoute;
import com.luvin.ai.service.story.AiEpisodePlan.BlockPlan;
import com.luvin.ai.service.story.AiEpisodePlan.ChoicePlan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 설계 단계: 회차 공통 사건·배경 장면, 블록 순서, 블록별 참여 빵, pair 상대, 다룰 열린 떡밥을 코드로 정한다.
 *
 * 순서(계약 0-1장): group → (2화만 단톡방) → transition → 첫 pair → (개인톡) → (10화 interview × 출연 빵 수)
 * → (3·5·6·8화 choice). transition은 첫 pair 바로 앞에 하나이고, 첫 pair 뒤에는 group이나 단톡방을 두지 않는다.
 *
 * 메신저: 단톡방(group)은 2화만, transition 앞. 개인톡(direct)은 첫 pair 뒤에 1·3·4·6·7·9화는 그 회차 1:1 상대와,
 * 5화는 후보 셋이 각자. 그 외 회차(2·8·10화)는 개인톡 없이 pair 뒤 바로 choice나 회차 끝이다.
 *
 * pair 상대(공통 데이터 계약 7장):
 * - 1·2·3화 = 시즌 시작 때 랜덤으로 정한 후보 순서의 첫째·둘째·셋째
 * - 4화 = ①에서 수락한 빵, 아니면(혼자 있기·거절) 친구 / 5화 = 친구 / 6화 = ②에서 수락한 빵, 아니면 친구
 * - 7·10화 = romance면 연인, solo면 친구 / 8화 = romance면 연인, solo면 호감이 가장 높은 후보
 * - 9화 = ④에서 수락한 빵(출연 빵 5명 중 하나), 아니면 혼자
 */
public final class AiEpisodePlanner {

    private static final int OPEN_THREAD_LIMIT = 2;
    /** 단톡방(group 메신저)은 "단톡방이 생김" 회차에만. */
    private static final int GROUP_CHAT_ORDER = 2;
    /** 후보 셋이 같은 회차 메신저에서 각자 다르게 연락하는 회차(나를 대하는 방식이 갈림). */
    private static final int CANDIDATES_CONTACT_ORDER = 5;
    /** 그 회차 1:1 상대와 개인톡(direct 메신저)이 이어지는 회차. 상대가 없으면(혼자) 메신저도 없다. */
    private static final Set<Integer> DIRECT_MESSAGE_ORDERS = Set.of(1, 3, 4, 6, 7, 9);
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
                        Map<UUID, List<StoryTypes.Thread>> openThreads, Map<Integer, ChoiceRecord> choicesByPoint) {
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
        blocks.add(block(nextId(blocks), AiSceneBlockType.GROUP, everyone, null, null));
        if (order == GROUP_CHAT_ORDER) {
            blocks.add(new BlockPlan(nextId(blocks), AiSceneBlockType.MESSENGER, everyone, MESSENGER_GROUP,
                    AiStoryCatalog.groupRoomName(input.setting()), null, null, false));
        }
        blocks.add(block(nextId(blocks), AiSceneBlockType.TRANSITION, List.of(), null, null));
        blocks.add(block(nextId(blocks), AiSceneBlockType.PAIR, pairParticipants, null, partner));

        if (order == CANDIDATES_CONTACT_ORDER) {
            for (AiStoryCastMember candidate : romanceCandidates(input.cast())) {
                blocks.add(directMessenger(nextId(blocks), me, candidate));
            }
        } else if (DIRECT_MESSAGE_ORDERS.contains(order) && partner != null) {
            blocks.add(directMessenger(nextId(blocks), me, member(input.cast(), partner)));
        }

        if (order == AiStoryCatalog.FINAL_ORDER) {
            for (AiStoryCastMember member : input.cast()) {
                if (!member.isMe()) {
                    blocks.add(block(nextId(blocks), AiSceneBlockType.INTERVIEW,
                            List.of(member.breadId()), null, member.breadId()));
                }
            }
        }

        Integer pointId = AiStoryCatalog.pointOf(order);
        if (pointId != null) {
            ChoicePlan choice = new ChoicePlan(pointId, AiStoryCatalog.question(pointId), candidates(input, pointId));
            blocks.add(new BlockPlan(nextId(blocks), AiSceneBlockType.CHOICE, List.of(), null, null, choice,
                    null, false));
        }

        return new AiEpisodePlan(order, input.setting(), AiStoryCatalog.commonEvent(order),
                AiStoryCatalog.settingScene(input.setting(), order), order >= 7 ? input.route() : null,
                AiStoryCatalog.referencePlot(order), openThreads(input, partner, blocks), List.copyOf(blocks));
    }

    /**
     * 선택 직후 보여줄 반응 장면(choice_result) 하나. 거절이면 FastAPI가 대체 장면(fallbackBlocks)을 함께 돌려주고
     * Spring이 그 뒤에 pair 블록으로 붙인다(5-3). 혼자 있기면 FastAPI를 부르지 않는다.
     */
    public static BlockPlan planChoiceReaction(int pointId, UUID breadId, AiChoiceJudge.Decision decision,
                                               List<AiStoryCastMember> cast) {
        AiStoryCastMember me = member(cast, AiCastRole.MY_BREAD);
        List<UUID> participants = breadId == null ? List.of(me.breadId()) : List.of(me.breadId(), breadId);
        return new BlockPlan("c" + pointId + "-1", AiSceneBlockType.CHOICE_RESULT, participants, null, null, null,
                breadId, decision.shortReply());
    }

    private static UUID pairPartner(Input input) {
        UUID friend = member(input.cast(), AiCastRole.FRIEND).breadId();
        boolean romance = input.route() == AiStoryRoute.ROMANCE && input.romancePartnerId() != null;
        return switch (input.order()) {
            case 1, 2, 3 -> romanceCandidates(input.cast()).get(input.order() - 1).breadId();
            case 4 -> acceptedBreadOr(input.choicesByPoint().get(1), friend);
            case 5 -> friend;
            case 6 -> acceptedBreadOr(input.choicesByPoint().get(2), friend);
            case 8 -> romance ? input.romancePartnerId() : favoriteCandidate(input);
            case 9 -> acceptedBreadOr(input.choicesByPoint().get(4), null);
            default -> romance ? input.romancePartnerId() : friend;
        };
    }

    private static UUID acceptedBreadOr(ChoiceRecord choice, UUID fallback) {
        if (choice != null && choice.breadId() != null && choice.accepted()) {
            return choice.breadId();
        }
        return fallback;
    }

    /**
     * ①~③은 연애 후보 3명. ④(8화)는 출연 빵 5명 전원 — 연애 후보 3명(만난 순서), 친구, 분위기 메이커 순이다. FastAPI는
     * 이 candidateIds 순서 그대로 후보를 쓰고(임의로 고르지 않는다), 앱 투표지도 이 5명 + 혼자 있기다.
     */
    private static List<UUID> candidates(Input input, int pointId) {
        List<UUID> romance = romanceCandidates(input.cast()).stream().map(AiStoryCastMember::breadId).toList();
        if (pointId != 4) {
            return romance;
        }
        List<UUID> everyone = new ArrayList<>(romance);
        everyone.add(member(input.cast(), AiCastRole.FRIEND).breadId());
        everyone.add(member(input.cast(), AiCastRole.MOOD_MAKER).breadId());
        return List.copyOf(everyone);
    }

    /** 호감이 가장 높은 후보. 같으면 먼저 만난 후보. */
    private static UUID favoriteCandidate(Input input) {
        List<AiStoryCastMember> candidates = romanceCandidates(input.cast());
        return candidates.stream()
                .max(Comparator.<AiStoryCastMember>comparingInt(c -> input.affinity().getOrDefault(c.breadId(), 0))
                        .thenComparingInt(c -> -candidates.indexOf(c)))
                .map(AiStoryCastMember::breadId)
                .orElseThrow();
    }

    /**
     * 연애 후보 셋을 만난 순서(candidateOrder)대로. 이 순서가 생기기 전에 시작한 시즌(candidateOrder null)은
     * 출연진 역할 순서를 쓴다.
     */
    private static List<AiStoryCastMember> romanceCandidates(List<AiStoryCastMember> cast) {
        return cast.stream().filter(c -> c.castRole().isRomanceCandidate())
                .sorted(Comparator.comparing(AiStoryCastMember::candidateOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AiStoryCastMember::castRole))
                .toList();
    }

    /**
     * 본문 입력 threads(최대 2개): 이번 회차 장면에 나오는 빵의 안 풀린 떡밥만, 1:1 상대의 것부터. 이번 회차에 나오지 않는
     * 빵의 떡밥은 보내지 않는다 — 현재 사건과 무관한 과거 떡밥이 이어 쓰이지 않게 한다.
     */
    private static List<StoryTypes.Thread> openThreads(Input input, UUID partner, List<BlockPlan> blocks) {
        Set<UUID> appearing = new HashSet<>();
        blocks.forEach(b -> appearing.addAll(b.participantIds()));
        List<StoryTypes.Thread> picked = new ArrayList<>();
        if (partner != null) {
            picked.addAll(input.openThreads().getOrDefault(partner, List.of()));
        }
        input.cast().stream()
                .filter(c -> !c.isMe() && !c.breadId().equals(partner) && appearing.contains(c.breadId()))
                .forEach(c -> picked.addAll(input.openThreads().getOrDefault(c.breadId(), List.of())));
        Set<String> seen = new HashSet<>();
        return picked.stream().filter(t -> seen.add(t.id())).limit(OPEN_THREAD_LIMIT).toList();
    }

    private static String nextId(List<BlockPlan> blocks) {
        return "b" + (blocks.size() + 1);
    }

    private static AiStoryCastMember member(List<AiStoryCastMember> cast, UUID breadId) {
        return cast.stream().filter(c -> c.breadId().equals(breadId)).findFirst().orElseThrow();
    }

    /** 1:1 메신저(개인톡). 방 이름은 상대 이름. */
    private static BlockPlan directMessenger(String id, AiStoryCastMember me, AiStoryCastMember partner) {
        return new BlockPlan(id, AiSceneBlockType.MESSENGER, List.of(me.breadId(), partner.breadId()), MESSENGER_DIRECT,
                partner.name(), null, partner.breadId(), false);
    }

    private static BlockPlan block(String id, AiSceneBlockType type, List<UUID> participants, String messengerKind,
                                   UUID focus) {
        return new BlockPlan(id, type, participants, messengerKind, null, null, focus, false);
    }

    private static AiStoryCastMember member(List<AiStoryCastMember> cast, AiCastRole role) {
        return cast.stream().filter(c -> c.castRole() == role).findFirst()
                .orElseThrow(() -> new IllegalStateException("출연진에 " + role + "이(가) 없습니다."));
    }
}

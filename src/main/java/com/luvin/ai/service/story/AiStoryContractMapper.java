package com.luvin.ai.service.story;

import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.domain.AiSceneBlockType;
import com.luvin.ai.dto.story.AiSceneBlockView;
import com.luvin.ai.dto.story.AiStageIntroView;
import com.luvin.ai.dto.story.AiStoryMessageView;
import com.luvin.ai.dto.story.AiStoryParticipantView;
import com.luvin.ai.service.story.AiEpisodePlan.BlockPlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spring 모델(시즌 캐릭터 UUID, 앱 응답 블록)과 공통 데이터 계약(역할 고정 빵 id, 설계·장면 블록) 사이의 변환.
 * 한 시즌 스냅샷 기준으로 만든다. 앱 응답 형식은 openapi.json, FastAPI 형식은 계약 5장이 기준이다.
 */
final class AiStoryContractMapper {

    private final AiStorySnapshot snapshot;
    private final Map<UUID, AiStageIntroView.StageCast> stageCast = new HashMap<>();

    AiStoryContractMapper(AiStorySnapshot snapshot, AiStageIntroView stageIntro) {
        this.snapshot = snapshot;
        if (stageIntro != null) {
            stageIntro.cast().forEach(c -> stageCast.put(c.breadId(), c));
        }
    }

    // ---------------------------------------------------------------- id

    String contractId(UUID breadId) {
        return snapshot.member(breadId).orElseThrow().castRole().contractId();
    }

    UUID uuid(String contractId) {
        return snapshot.memberByContractId(contractId)
                .orElseThrow(() -> new AiStoryInvalidResponseException("출연진에 없는 빵 id: " + contractId))
                .breadId();
    }

    /** 응답의 빵 id가 null이면 null(혼자 있기, 솔로 최종 상대 등). */
    UUID uuidOrNull(String contractId) {
        return contractId == null ? null : uuid(contractId);
    }

    String contractIdOrNull(UUID breadId) {
        return breadId == null ? null : contractId(breadId);
    }

    Set<String> contractIds(List<UUID> breadIds) {
        Set<String> ids = new HashSet<>();
        breadIds.forEach(id -> ids.add(contractId(id)));
        return ids;
    }

    List<String> castIds() {
        return snapshot.cast().stream().map(c -> c.castRole().contractId()).toList();
    }

    List<String> relationIds() {
        return snapshot.relations().stream().map(r -> contractId(r.breadId())).toList();
    }

    // ---------------------------------------------------------------- FastAPI 입력

    /** 무대 소개 전이면 role은 시즌 역할(7장), description은 null이다. */
    List<StoryTypes.CastMember> castMembers() {
        return snapshot.cast().stream().map(m -> {
            AiStageIntroView.StageCast intro = stageCast.get(m.breadId());
            return new StoryTypes.CastMember(m.castRole().contractId(), m.name(), m.breadType(),
                    intro != null ? intro.role() : m.castRole().seasonRole(),
                    intro != null ? intro.description() : null, m.gender(), m.castRole().fastApiCastRole(),
                    m.castRole().attachmentStyle().wireValue());
        }).toList();
    }

    List<StoryTypes.RelationRecord> relationRecords() {
        return snapshot.relations().stream().map(this::relationRecord).toList();
    }

    StoryTypes.RelationRecord relationRecord(UUID breadId) {
        return relationRecord(snapshot.relation(breadId).orElseThrow());
    }

    private StoryTypes.RelationRecord relationRecord(AiStorySnapshot.Relation r) {
        return new StoryTypes.RelationRecord(contractId(r.breadId()), AiChoiceRules.affinityLevel(r.affinity()),
                r.statusText(), r.memorySummary() == null ? "" : r.memorySummary(), r.recentMemories(),
                r.knownFacts(), r.openThreads());
    }

    List<StoryTypes.ChoiceRecord> choiceRecords() {
        return snapshot.choices().stream()
                .map(c -> new StoryTypes.ChoiceRecord(c.pointId(), c.episodeOrder(), contractIdOrNull(c.breadId()),
                        c.accepted()))
                .toList();
    }

    List<StoryTypes.ChoiceRecordWithReason> choiceRecordsWithReason() {
        return snapshot.choices().stream()
                .map(c -> new StoryTypes.ChoiceRecordWithReason(c.pointId(), c.episodeOrder(),
                        contractIdOrNull(c.breadId()), c.accepted(),
                        AiChoiceRules.rejectReason(c.accepted(), c.reasonCode())))
                .toList();
    }

    StoryTypes.BlockDesign design(BlockPlan plan) {
        List<String> participants = plan.participantIds().stream().map(this::contractId).toList();
        return switch (plan.type()) {
            case PAIR -> new StoryTypes.BlockDesign(plan.id(), plan.type().wireValue(), participants,
                    plan.focusBreadId() == null ? StoryTypes.ALONE : contractId(plan.focusBreadId()),
                    plan.shortReply(), null, null, null);
            case MESSENGER -> new StoryTypes.BlockDesign(plan.id(), plan.type().wireValue(), participants, null, null,
                    new StoryTypes.Messenger(plan.messengerKind(), plan.roomName()), null, null);
            case CHOICE -> new StoryTypes.BlockDesign(plan.id(), plan.type().wireValue(), participants, null, null,
                    null, plan.choice().pointId(),
                    plan.choice().candidateIds().stream().map(this::contractId).toList());
            default -> new StoryTypes.BlockDesign(plan.id(), plan.type().wireValue(), participants, null, null,
                    null, null, null);
        };
    }

    AiStoryResponseValidator.ExpectedBlock expected(BlockPlan plan) {
        Set<String> candidates = plan.choice() == null ? Set.of()
                : contractIds(plan.choice().candidateIds());
        return new AiStoryResponseValidator.ExpectedBlock(plan.id(), plan.type(),
                contractIds(plan.participantIds()), candidates);
    }

    /** 정리 단계(5-5) 입력: 재생된 블록을 계약 SceneBlock으로. 선택 결과 블록들은 choice_result 하나로 묶는다. */
    List<StoryTypes.SceneBlock> sceneBlocks(List<AiSceneBlockView> episodeBlocks, List<AiSceneBlockView> resultBlocks) {
        List<StoryTypes.SceneBlock> blocks = new ArrayList<>();
        for (AiSceneBlockView view : episodeBlocks) {
            blocks.add(sceneBlock(view));
        }
        if (!resultBlocks.isEmpty()) {
            AiSceneBlockView reaction = resultBlocks.get(0);
            List<StoryTypes.SceneBlock> fallbacks = resultBlocks.subList(1, resultBlocks.size()).stream()
                    .map(this::sceneBlock).toList();
            AiSceneBlockView.SceneChoiceResult result = reaction.choiceResult();
            blocks.add(new StoryTypes.SceneBlock(reaction.id(), AiSceneBlockType.CHOICE_RESULT.wireValue(),
                    participants(reaction), null, null, null, null,
                    new StoryTypes.ChoiceResult(result.pointId(), contractIdOrNull(result.breadId()), result.accepted(),
                            messages(reaction), fallbacks),
                    null));
        }
        return blocks;
    }

    private StoryTypes.SceneBlock sceneBlock(AiSceneBlockView view) {
        StoryTypes.Messenger messenger = view.messenger() == null ? null
                : new StoryTypes.Messenger(view.messenger().kind(), view.messenger().roomName());
        StoryTypes.ChoiceBlock choice = view.choice() == null ? null
                : new StoryTypes.ChoiceBlock(view.choice().pointId(), view.choice().question(),
                        view.choice().candidates().stream()
                                .map(c -> new StoryTypes.ChoiceCandidate(contractId(c.breadId()), c.memory()))
                                .toList());
        return new StoryTypes.SceneBlock(view.id(), view.type(), participants(view), messages(view),
                view.transitionText(), messenger, choice, null,
                view.interview() == null ? null : contractId(view.interview().breadId()));
    }

    private List<StoryTypes.Participant> participants(AiSceneBlockView view) {
        if (view.participants() == null) {
            return null;
        }
        return view.participants().stream()
                .map(p -> new StoryTypes.Participant(contractId(p.breadId()), p.name(), p.breadType(), p.stageRole()))
                .toList();
    }

    private List<StoryTypes.Message> messages(AiSceneBlockView view) {
        if (view.messages() == null) {
            return null;
        }
        return view.messages().stream()
                .map(m -> new StoryTypes.Message(m.messageId().toString(),
                        m.speakerId() == null ? StoryTypes.NARRATOR : contractId(m.speakerId()), m.text(), m.kind(),
                        m.read(), m.typing(), m.deletedAfterSend(), m.sentAtLabel()))
                .toList();
    }

    // ---------------------------------------------------------------- 앱 응답

    AiStoryParticipantView participant(UUID breadId) {
        AiStoryCastMember member = snapshot.member(breadId).orElseThrow();
        AiStageIntroView.StageCast intro = stageCast.get(breadId);
        return new AiStoryParticipantView(breadId, member.name(), member.castRole().wireValue(),
                member.castRole().attachmentStyle().wireValue(), member.isMe(), member.breadType(),
                intro == null ? null : intro.role());
    }

    List<AiStoryParticipantView> participants(List<UUID> breadIds) {
        return breadIds.stream().map(this::participant).toList();
    }

    /** FastAPI 대사 → 앱 대사. 메시지 id는 Spring이 새로 붙인다(피드백·좋아요가 가리키는 id). */
    List<AiStoryMessageView> messageViews(List<StoryTypes.Message> messages, int[] sequence) {
        UUID me = snapshot.me().breadId();
        List<AiStoryMessageView> views = new ArrayList<>();
        for (StoryTypes.Message m : messages) {
            UUID speaker = StoryTypes.NARRATOR.equals(m.speakerId()) ? null : uuid(m.speakerId());
            views.add(new AiStoryMessageView(UUID.randomUUID(), sequence[0]++, speaker, m.kind(),
                    me.equals(speaker), m.text(), m.read(), m.typing(), m.deletedAfterSend(), m.sentAtLabel()));
        }
        return views;
    }
}

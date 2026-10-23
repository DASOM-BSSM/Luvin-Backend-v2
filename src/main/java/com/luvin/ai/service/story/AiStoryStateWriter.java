package com.luvin.ai.service.story;

import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.domain.AiBreadRelation;
import com.luvin.ai.domain.AiCastRole;
import com.luvin.ai.domain.AiCharacterRole;
import com.luvin.ai.domain.AiEpisodeContent;
import com.luvin.ai.domain.AiSeason;
import com.luvin.ai.domain.AiSeasonCharacter;
import com.luvin.ai.domain.AiSeasonChoice;
import com.luvin.ai.domain.AiSeasonSetting;
import com.luvin.ai.domain.AiSeasonStoryReport;
import com.luvin.ai.domain.AiStoryContentStatus;
import com.luvin.ai.domain.AiStoryFeedback;
import com.luvin.ai.domain.AiStoryMessageLike;
import com.luvin.ai.domain.AiStoryRoute;
import com.luvin.ai.domain.PartnerLetter;
import com.luvin.ai.repository.AiBreadRelationRepository;
import com.luvin.ai.repository.AiEpisodeContentRepository;
import com.luvin.ai.repository.AiSeasonCharacterRepository;
import com.luvin.ai.repository.AiSeasonChoiceRepository;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.ai.repository.AiSeasonStoryReportRepository;
import com.luvin.ai.repository.AiStoryFeedbackRepository;
import com.luvin.ai.repository.AiStoryMessageLikeRepository;
import com.luvin.ai.repository.PartnerLetterRepository;
import com.luvin.ai.service.exception.AiSeasonNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * 장면 블록 회차의 모든 짧은 write transaction. {@link com.luvin.ai.service.AiSeasonStateWriter}와 같은 이유로
 * FastAPI 호출을 하는 {@link AiStoryService}와 분리했다(self-invocation 방지, 원격 호출 중 DB 커넥션 미점유).
 */
@Service
@RequiredArgsConstructor
public class AiStoryStateWriter {

    /** recent_memories는 최근 3회차 주요 장면만 남긴다. */
    static final int RECENT_MEMORY_LIMIT = 3;

    private static final List<AiCastRole> CANDIDATE_ROLES = List.of(AiCastRole.AVOIDANT, AiCastRole.ANXIOUS, AiCastRole.SECURE);
    private static final List<AiCastRole> SUPPORTING_ROLES = List.of(AiCastRole.FRIEND, AiCastRole.MOOD_MAKER);

    private final AiSeasonRepository seasonRepository;
    private final AiSeasonCharacterRepository characterRepository;
    private final AiBreadRelationRepository relationRepository;
    private final AiSeasonChoiceRepository choiceRepository;
    private final AiEpisodeContentRepository contentRepository;
    private final AiSeasonStoryReportRepository reportRepository;
    private final PartnerLetterRepository letterRepository;
    private final AiStoryFeedbackRepository feedbackRepository;
    private final AiStoryMessageLikeRepository likeRepository;

    /** GET content 한 번에 시작 여부를 확인하기 위해 생성 접수 결과로 돌려준다. */
    public record GenerationTicket(Long contentPk, boolean started) {
    }

    /**
     * 새 흐름을 시작한다: 배경 저장, 출연진 배정(representative → 내 빵, AI candidate 3명 → 회피형/불안형/안정형,
     * 친구·분위기 메이커 추가), 출연 빵별 관계 기록 생성. 이미 시작한 시즌이면 아무것도 하지 않는다.
     *
     * 이름: 내 빵은 사용자 닉네임과 설문 빵 타입, 나머지는 "중립 형용사 + 빵 종류"로 시즌 동안 고정한다. 한 시즌 안에서
     * 이름과 빵 종류가 겹치지 않는다(빵 종류 8종, 형용사 12개에서 섞어 뽑는다).
     */
    @Transactional
    public void startStory(Long seasonPk, AiSeasonSetting setting, String myName, String myBreadType,
                           List<String> breadTypes, RandomGenerator random) {
        AiSeason season = seasonRepository.findWithLockById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
        if (season.isStoryStarted()) {
            return;
        }
        List<AiSeasonCharacter> characters = characterRepository.findAllBySeason_Id(seasonPk);
        AiSeasonCharacter representative = characters.stream().filter(AiSeasonCharacter::isRepresentative)
                .findFirst().orElseThrow(() -> new IllegalStateException("시즌에 representative가 없습니다."));
        List<AiSeasonCharacter> candidates = characters.stream()
                .filter(c -> c.getRole() == AiCharacterRole.CANDIDATE)
                .sorted(Comparator.comparing(AiSeasonCharacter::getId))
                .toList();
        if (candidates.size() < CANDIDATE_ROLES.size()) {
            throw new IllegalStateException("시즌 candidate가 3명 미만입니다: " + candidates.size());
        }

        List<String> otherBreads = new ArrayList<>(breadTypes);
        otherBreads.remove(myBreadType);
        Collections.shuffle(otherBreads, random);
        List<String> adjectives = new ArrayList<>(AiStoryCatalog.NEUTRAL_ADJECTIVES);
        Collections.shuffle(adjectives, random);
        int[] next = {0};
        java.util.function.Supplier<String[]> nextName = () -> {
            String bread = otherBreads.get(next[0]);
            String adjective = adjectives.get(next[0]++);
            return new String[]{adjective + " " + bread, bread};
        };

        representative.assignCast(AiCastRole.MY_BREAD, myName, myBreadType);
        List<AiSeasonCharacter> cast = new ArrayList<>();
        for (int i = 0; i < CANDIDATE_ROLES.size(); i++) {
            String[] name = nextName.get();
            candidates.get(i).assignCast(CANDIDATE_ROLES.get(i), name[0], name[1]);
            cast.add(candidates.get(i));
        }
        for (AiCastRole role : SUPPORTING_ROLES) {
            String[] name = nextName.get();
            cast.add(characterRepository.save(new AiSeasonCharacter(
                    season, UUID.randomUUID(), role, name[0], name[1])));
        }
        for (AiSeasonCharacter member : cast) {
            relationRepository.save(new AiBreadRelation(season.getMemberId(), season, member.getCharacterId(),
                    AiChoiceRules.INITIAL_AFFINITY.get(member.getCastRole()), AiChoiceRules.INITIAL_STATUS_TEXT));
        }
        season.startStory(setting);
    }

    @Transactional(readOnly = true)
    public AiStorySnapshot snapshot(Long seasonPk) {
        AiSeason season = seasonRepository.findById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
        List<AiStoryCastMember> cast = characterRepository.findAllBySeason_Id(seasonPk).stream()
                .filter(c -> c.getCastRole() != null)
                .sorted(Comparator.comparing(AiSeasonCharacter::getCastRole))
                .map(c -> new AiStoryCastMember(c.getCharacterId(), c.getCastRole(), c.getName(), c.getBreadType(),
                        c.getGender()))
                .toList();
        List<AiStorySnapshot.Relation> relations = relationRepository.findAllBySeason_Id(seasonPk).stream()
                .map(r -> new AiStorySnapshot.Relation(r.getTargetBreadId(), r.getAffinity(), r.getRejectCount(),
                        r.getStatusText(), r.getMemorySummary(), AiStoryJson.readStrings(r.getRecentMemoriesJson()),
                        AiStoryJson.readStrings(r.getKnownFactsJson()), AiStoryJson.readThreads(r.getOpenThreadsJson()),
                        r.getChoiceMemory()))
                .toList();
        List<AiStorySnapshot.Choice> choices = choiceRepository.findAllBySeason_IdOrderByPointIdAsc(seasonPk).stream()
                .map(c -> new AiStorySnapshot.Choice(c.getPointId(), c.getEpisodeOrder(), c.getBreadId(),
                        c.isAccepted(), c.getReasonCode(), c.getResultBlocksJson()))
                .toList();
        List<AiStorySnapshot.Content> contents = contentRepository.findAllBySeason_IdOrderByEpisodeOrderAsc(seasonPk)
                .stream()
                .map(c -> new AiStorySnapshot.Content(c.getId(), c.getEpisodeOrder(), c.getStatus(), c.getRoute(),
                        c.getErrorCode(), c.getIntroJson(), c.getStageIntroJson(), c.getBlocksJson(),
                        c.getSummaryJson(), c.getRequestedAt(), c.isCompleted()))
                .toList();
        return new AiStorySnapshot(season.getId(), season.getSeasonId(), season.getMemberId(), season.getSeasonNumber(),
                season.getStatus(), season.getSetting(), season.getCurrentOrder(), season.getRoute(),
                season.getRomancePartnerId(), cast, relations, choices, contents);
    }

    /**
     * 회차 생성을 접수한다. 처음이거나, FAILED였거나, GENERATING인 채 staleBefore보다 오래 멈춰 있으면 started=true로
     * 돌려주고 호출부가 생성을 시작한다. 동시에 처음 요청한 두 요청 중 진 쪽은 unique 제약 위반을 이 짧은 트랜잭션
     * 안에서만 받고(REQUIRES_NEW + saveAndFlush) 이긴 쪽 row를 그대로 쓴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GenerationTicket beginGeneration(Long seasonPk, int order, AiStoryRoute route, LocalDateTime staleBefore) {
        AiEpisodeContent existing = contentRepository.findBySeason_IdAndEpisodeOrder(seasonPk, order).orElse(null);
        if (existing == null) {
            AiSeason season = seasonRepository.findById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
            try {
                AiEpisodeContent created = contentRepository.saveAndFlush(
                        new AiEpisodeContent(season.getMemberId(), season, order, route));
                return new GenerationTicket(created.getId(), true);
            } catch (DataIntegrityViolationException raceLoser) {
                AiEpisodeContent winner = contentRepository.findBySeason_IdAndEpisodeOrder(seasonPk, order)
                        .orElseThrow(() -> raceLoser);
                return new GenerationTicket(winner.getId(), false);
            }
        }
        boolean stale = existing.getStatus() == AiStoryContentStatus.GENERATING
                && existing.getRequestedAt().isBefore(staleBefore);
        if (existing.getStatus() == AiStoryContentStatus.FAILED || stale) {
            existing.restartGeneration(route);
            contentRepository.saveAndFlush(existing);
            return new GenerationTicket(existing.getId(), true);
        }
        return new GenerationTicket(existing.getId(), false);
    }

    @Transactional
    public void saveGenerated(Long contentPk, String introJson, String stageIntroJson, String blocksJson) {
        contentRepository.findById(contentPk).ifPresent(c -> c.markReady(introJson, stageIntroJson, blocksJson));
    }

    @Transactional
    public void markGenerationFailed(Long contentPk, String errorCode) {
        contentRepository.findById(contentPk).ifPresent(c -> c.markFailed(errorCode));
    }

    /**
     * 선택을 기록하고 관계·흐름에 반영한다: 수락이면 호감 +8, 거절이면 거절 횟수 +1, ③이면 route 결정.
     * 같은 지점을 동시에 두 번 보내면 unique 제약 위반(DataIntegrityViolationException)으로 트랜잭션 전체가 롤백된다.
     */
    @Transactional
    public void saveChoice(Long seasonPk, int pointId, int order, UUID breadId, AiChoiceJudge.Decision decision,
                           String resultBlocksJson) {
        AiSeason season = seasonRepository.findById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
        choiceRepository.saveAndFlush(new AiSeasonChoice(season.getMemberId(), season, pointId, order, breadId,
                decision.accepted(), decision.reasonCode(), resultBlocksJson));

        if (breadId != null) {
            AiBreadRelation relation = relationRepository.findBySeason_IdAndTargetBreadId(seasonPk, breadId)
                    .orElseThrow(() -> new IllegalStateException("관계 기록이 없는 빵: " + breadId));
            if (decision.accepted()) {
                relation.addAffinity(AiChoiceRules.ACCEPTED_AFFINITY_BONUS);
            } else {
                relation.recordRejection();
            }
            if (pointId == AiChoiceRules.CONFESSION_POINT && decision.accepted()) {
                relation.updateStatusText(AiChoiceRules.ROMANCE_STATUS_TEXT);
            }
        }
        if (pointId == AiChoiceRules.CONFESSION_POINT) {
            boolean romance = breadId != null && decision.accepted();
            season.applyRoute(romance ? AiStoryRoute.ROMANCE : AiStoryRoute.SOLO, romance ? breadId : null);
        }
    }

    /** 정리 단계(5-5)가 제안한 빵 하나의 관계 기록 갱신안. breadId는 서비스가 계약 id를 UUID로 바꾼 값이다. */
    public record RelationChange(UUID breadId, String memorySummary, String recentMemory, List<String> newFacts,
                                 List<StoryTypes.Thread> threadsAdd, List<String> threadsResolved, String statusText,
                                 Integer affinityDelta, String choiceMemory) {
    }

    /** 회차 쪽지(5-6). partnerId는 발신 빵(계약 id를 UUID로 바꾼 값, 모르면 null). */
    public record EpisodeNote(UUID partnerId, String body, String renderMode) {
    }

    /**
     * 회차 완료: 정리 단계 결과를 관계 기록에 반영(호감 변화는 -5~+5로 잘라서), 회차 요약 저장, 회차 쪽지 저장,
     * 다음 회차로 진행. 10화면 최종 리포트를 저장하고 시즌을 완료 처리한다. 이미 완료한 회차면 아무것도 하지 않는다.
     */
    @Transactional
    public void completeEpisode(Long seasonPk, int order, String episodeSummary, List<RelationChange> changes,
                                EpisodeNote note, String reportJson) {
        AiSeason season = seasonRepository.findById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
        AiEpisodeContent content = contentRepository.findBySeason_IdAndEpisodeOrder(seasonPk, order).orElseThrow();
        if (content.isCompleted()) {
            return;
        }

        Map<UUID, AiBreadRelation> relations = relationRepository.findAllBySeason_Id(seasonPk).stream()
                .collect(Collectors.toMap(AiBreadRelation::getTargetBreadId, Function.identity()));
        for (RelationChange change : changes) {
            AiBreadRelation relation = relations.get(change.breadId());
            if (relation == null) {
                continue;
            }
            if (change.affinityDelta() != null) {
                relation.addAffinity(AiChoiceRules.clampSummaryDelta(change.affinityDelta()));
            }
            String statusText = AiChoiceRules.ROMANCE_STATUS_TEXT.equals(relation.getStatusText())
                    ? null : change.statusText();
            relation.applyMemory(change.memorySummary(), appendRecentMemory(relation, change.recentMemory()),
                    appendFacts(relation, change.newFacts()),
                    updateThreads(relation, change.threadsAdd(), change.threadsResolved()), statusText,
                    change.choiceMemory());
        }

        content.markCompleted(AiStoryJson.write(Map.of("episodeSummary", episodeSummary)));
        if (!letterRepository.existsBySeason_IdAndEpisodeOrder(seasonPk, order)) {
            letterRepository.save(PartnerLetter.forEpisode(season.getMemberId(), season, order,
                    note.partnerId(), note.body(), note.renderMode()));
        }

        if (order < AiStoryCatalog.FINAL_ORDER) {
            season.advanceOrder(order + 1);
            return;
        }
        if (reportRepository.findBySeason_Id(seasonPk).isEmpty()) {
            reportRepository.save(new AiSeasonStoryReport(season, reportJson));
        }
        season.complete();
    }

    // ---------------------------------------------------------------- 1:1 장면 피드백·좋아요

    /** 메시지당 피드백 하나. 이미 있으면 글만 바꾼다. */
    @Transactional
    public AiStoryFeedback saveFeedback(Long seasonPk, int order, String blockId, UUID messageId, String messageText,
                                       String text) {
        Optional<AiStoryFeedback> existing = feedbackRepository.findBySeason_IdAndMessageId(seasonPk, messageId);
        if (existing.isPresent()) {
            existing.get().rewrite(text);
            return existing.get();
        }
        return feedbackRepository.save(new AiStoryFeedback(seasonRepository.getReferenceById(seasonPk), order,
                blockId, messageId, messageText, text));
    }

    /** liked면 좋아요를 켜고(이미 켜져 있으면 그대로), 아니면 끈다. */
    @Transactional
    public void setLike(Long seasonPk, int order, String blockId, UUID messageId, String messageText, boolean fromMe,
                        boolean liked) {
        Optional<AiStoryMessageLike> existing = likeRepository.findBySeason_IdAndMessageId(seasonPk, messageId);
        if (liked && existing.isEmpty()) {
            likeRepository.save(new AiStoryMessageLike(seasonRepository.getReferenceById(seasonPk), order, blockId,
                    messageId, messageText, fromMe));
        } else if (!liked) {
            existing.ifPresent(likeRepository::delete);
        }
    }

    @Transactional(readOnly = true)
    public List<AiStoryFeedback> findFeedbacks(Long seasonPk, int order) {
        return feedbackRepository.findAllBySeason_IdAndEpisodeOrder(seasonPk, order);
    }

    @Transactional(readOnly = true)
    public List<UUID> findLikedMessageIds(Long seasonPk, int order) {
        return likeRepository.findAllBySeason_IdAndEpisodeOrder(seasonPk, order).stream()
                .map(AiStoryMessageLike::getMessageId).toList();
    }

    /** 학습 입력(learnedProfile.feedbacks): 최근 10개. */
    @Transactional(readOnly = true)
    public List<StoryTypes.Feedback> recentFeedbacks(Long seasonPk) {
        return feedbackRepository.findTop10BySeason_IdOrderByUpdatedAtDesc(seasonPk).stream()
                .map(f -> new StoryTypes.Feedback(f.getEpisodeOrder(), f.getMessageText(), f.getText()))
                .toList();
    }

    /** 학습 입력(learnedProfile.likedMessages): 내 빵의 대사 중 최근 10개. */
    @Transactional(readOnly = true)
    public List<String> recentLikedMyMessages(Long seasonPk) {
        return likeRepository.findTop10BySeason_IdAndFromMeTrueOrderByCreatedAtDesc(seasonPk).stream()
                .map(AiStoryMessageLike::getMessageText).toList();
    }

    @Transactional(readOnly = true)
    public Optional<String> findReportJson(Long seasonPk) {
        return reportRepository.findBySeason_Id(seasonPk).map(AiSeasonStoryReport::getReportJson);
    }

    /** 개발 전용: 시즌을 새 흐름 시작 전 상태로 되돌린다. 기존 5화 흐름 데이터(AI 시즌, 쪽지)는 건드리지 않는다. */
    @Transactional
    public void resetStory(Long seasonPk) {
        AiSeason season = seasonRepository.findWithLockById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
        choiceRepository.deleteAllBySeason_Id(seasonPk);
        relationRepository.deleteAllBySeason_Id(seasonPk);
        contentRepository.deleteAllBySeason_Id(seasonPk);
        reportRepository.deleteAllBySeason_Id(seasonPk);
        feedbackRepository.deleteAllBySeason_Id(seasonPk);
        likeRepository.deleteAllBySeason_Id(seasonPk);
        letterRepository.deleteAllBySeason_IdAndEpisodeOrderIsNotNull(seasonPk);
        characterRepository.deleteAllBySeason_IdAndRole(seasonPk, AiCharacterRole.SUPPORTING);
        characterRepository.findAllBySeason_Id(seasonPk).forEach(AiSeasonCharacter::clearCast);
        season.resetStory();
    }

    private static String appendFacts(AiBreadRelation relation, List<String> newFacts) {
        if (newFacts == null || newFacts.isEmpty()) {
            return null;
        }
        java.util.LinkedHashSet<String> facts = new java.util.LinkedHashSet<>(
                AiStoryJson.readStrings(relation.getKnownFactsJson()));
        facts.addAll(newFacts);
        return AiStoryJson.write(List.copyOf(facts));
    }

    /** 풀린 떡밥(id)은 빼고 새 떡밥은 더한다. 같은 id가 다시 오면 문구를 바꾼다. */
    private static String updateThreads(AiBreadRelation relation, List<StoryTypes.Thread> added, List<String> resolved) {
        if ((added == null || added.isEmpty()) && (resolved == null || resolved.isEmpty())) {
            return null;
        }
        Map<String, StoryTypes.Thread> threads = new java.util.LinkedHashMap<>();
        AiStoryJson.readThreads(relation.getOpenThreadsJson()).forEach(t -> threads.put(t.id(), t));
        if (resolved != null) {
            resolved.forEach(threads::remove);
        }
        if (added != null) {
            added.forEach(t -> threads.put(t.id(), t));
        }
        return AiStoryJson.write(List.copyOf(threads.values()));
    }

    private static String appendRecentMemory(AiBreadRelation relation, String recentMemory) {
        if (recentMemory == null || recentMemory.isBlank()) {
            return null;
        }
        List<String> memories = new ArrayList<>(AiStoryJson.readStrings(relation.getRecentMemoriesJson()));
        memories.add(recentMemory);
        while (memories.size() > RECENT_MEMORY_LIMIT) {
            memories.remove(0);
        }
        return AiStoryJson.write(memories);
    }
}

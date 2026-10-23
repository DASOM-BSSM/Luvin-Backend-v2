package com.luvin.ai.service.story;

import com.luvin.ai.client.dto.LetterResponseDto;
import com.luvin.ai.client.story.SummarizeResponseDto;
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
import com.luvin.ai.domain.AiStoryRoute;
import com.luvin.ai.domain.PartnerLetter;
import com.luvin.ai.repository.AiBreadRelationRepository;
import com.luvin.ai.repository.AiEpisodeContentRepository;
import com.luvin.ai.repository.AiSeasonCharacterRepository;
import com.luvin.ai.repository.AiSeasonChoiceRepository;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.ai.repository.AiSeasonStoryReportRepository;
import com.luvin.ai.repository.PartnerLetterRepository;
import com.luvin.ai.service.exception.AiSeasonNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
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

    /** GET content 한 번에 시작 여부를 확인하기 위해 생성 접수 결과로 돌려준다. */
    public record GenerationTicket(Long contentPk, boolean started) {
    }

    /**
     * 새 흐름을 시작한다: 배경 저장, 출연진 배정(representative → 내 빵, AI candidate 3명 → 회피형/불안형/안정형,
     * 친구·분위기 메이커 추가), 출연 빵별 관계 기록 생성. 이미 시작한 시즌이면 아무것도 하지 않는다.
     */
    @Transactional
    public void startStory(Long seasonPk, AiSeasonSetting setting) {
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

        representative.assignCast(AiCastRole.MY_BREAD, AiStoryCatalog.placeholderName(AiCastRole.MY_BREAD));
        List<AiSeasonCharacter> cast = new ArrayList<>();
        for (int i = 0; i < CANDIDATE_ROLES.size(); i++) {
            AiCastRole role = CANDIDATE_ROLES.get(i);
            candidates.get(i).assignCast(role, AiStoryCatalog.placeholderName(role));
            cast.add(candidates.get(i));
        }
        for (AiCastRole role : SUPPORTING_ROLES) {
            cast.add(characterRepository.save(new AiSeasonCharacter(
                    season, UUID.randomUUID(), role, AiStoryCatalog.placeholderName(role))));
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
                .map(c -> new AiStoryCastMember(c.getCharacterId(), c.getCastRole(), c.getName(), c.getGender()))
                .toList();
        List<AiStorySnapshot.Relation> relations = relationRepository.findAllBySeason_Id(seasonPk).stream()
                .map(r -> new AiStorySnapshot.Relation(r.getTargetBreadId(), r.getAffinity(), r.getRejectCount(),
                        r.getStatusText(), r.getMemorySummary(), AiStoryJson.readStrings(r.getRecentMemoriesJson()),
                        AiStoryJson.readStrings(r.getKnownFactsJson()), AiStoryJson.readStrings(r.getOpenThreadsJson())))
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

    /**
     * 회차 완료: 정리 단계 결과를 관계 기록에 반영(호감 변화는 -5~+5로 잘라서), 회차 요약 저장, 회차 쪽지 저장,
     * 다음 회차로 진행. 10화면 최종 리포트를 저장하고 시즌을 완료 처리한다. 이미 완료한 회차면 아무것도 하지 않는다.
     */
    @Transactional
    public void completeEpisode(Long seasonPk, int order, SummarizeResponseDto summary, LetterResponseDto note,
                                String reportJson) {
        AiSeason season = seasonRepository.findById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
        AiEpisodeContent content = contentRepository.findBySeason_IdAndEpisodeOrder(seasonPk, order).orElseThrow();
        if (content.isCompleted()) {
            return;
        }

        Map<UUID, AiBreadRelation> relations = relationRepository.findAllBySeason_Id(seasonPk).stream()
                .collect(Collectors.toMap(AiBreadRelation::getTargetBreadId, Function.identity()));
        for (SummarizeResponseDto.RelationUpdate update : summary.relations()) {
            AiBreadRelation relation = relations.get(update.breadId());
            if (relation == null) {
                continue;
            }
            if (update.affinityDelta() != null) {
                relation.addAffinity(AiChoiceRules.clampSummaryDelta(update.affinityDelta()));
            }
            String statusText = AiChoiceRules.ROMANCE_STATUS_TEXT.equals(relation.getStatusText())
                    ? null : update.statusText();
            relation.applyMemory(update.memorySummary(), appendRecentMemory(relation, update.recentMemory()),
                    update.knownFacts() == null ? null : AiStoryJson.write(update.knownFacts()),
                    update.openThreads() == null ? null : AiStoryJson.write(update.openThreads()), statusText);
        }

        content.markCompleted(AiStoryJson.write(Map.of("episodeSummary", summary.episodeSummary())));
        if (!letterRepository.existsBySeason_IdAndEpisodeOrder(seasonPk, order)) {
            letterRepository.save(PartnerLetter.forEpisode(season.getMemberId(), season, order,
                    note.finalPartnerId(), note.body(), note.renderMode()));
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
        letterRepository.deleteAllBySeason_IdAndEpisodeOrderIsNotNull(seasonPk);
        characterRepository.deleteAllBySeason_IdAndRole(seasonPk, AiCharacterRole.SUPPORTING);
        characterRepository.findAllBySeason_Id(seasonPk).forEach(AiSeasonCharacter::clearCast);
        season.resetStory();
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

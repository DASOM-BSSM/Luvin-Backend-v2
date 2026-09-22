package com.luvin.simulation.service;

import com.luvin.common.exception.EpisodeNotFoundException;
import com.luvin.simulation.domain.AiClone;
import com.luvin.simulation.domain.Episode;
import com.luvin.simulation.domain.EpisodeParticipant;
import com.luvin.simulation.domain.ParticipantType;
import com.luvin.simulation.domain.SimulationCharacter;
import com.luvin.simulation.dto.EpisodeCreateRequest;
import com.luvin.simulation.dto.EpisodeResponse;
import com.luvin.simulation.dto.ParticipantResponse;
import com.luvin.simulation.repository.AiCloneRepository;
import com.luvin.simulation.repository.EpisodeParticipantRepository;
import com.luvin.simulation.repository.EpisodeRepository;
import com.luvin.simulation.repository.SimulationCharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EpisodeServiceImpl implements EpisodeService {

    private final EpisodeRepository episodeRepository;
    private final EpisodeParticipantRepository episodeParticipantRepository;
    private final SimulationCharacterRepository simulationCharacterRepository;
    private final AiCloneRepository aiCloneRepository;

    @Override
    @Transactional(readOnly = true)
    public List<EpisodeResponse> getEpisodes(Long memberId) {
        return episodeRepository.findAllByMemberIdOrderByEpisodeNumberAsc(memberId).stream()
                .map(EpisodeResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EpisodeResponse getEpisode(Long memberId, Long episodeId) {
        return EpisodeResponse.from(getEpisodeOrThrow(memberId, episodeId));
    }

    @Override
    @Transactional
    public EpisodeResponse createEpisode(Long memberId, EpisodeCreateRequest request) {
        Episode episode = episodeRepository.save(
                new Episode(memberId, request.getEpisodeNumber(), request.getTitle()));

        // "나" 참가자: 유저의 AiClone 이름을 스냅샷으로 저장. 아직 AiClone이 없으면 기본 이름 사용.
        String selfName = aiCloneRepository.findTopByUserIdOrderByIdDesc(memberId)
                .map(AiClone::getCloneName)
                .orElse("나의 분신");
        episodeParticipantRepository.save(
                new EpisodeParticipant(episode, ParticipantType.SELF, null, selfName));

        // 고정 AI 출연진 풀 전체를 이 에피소드의 참가자로 등록
        for (SimulationCharacter character : simulationCharacterRepository.findAll()) {
            episodeParticipantRepository.save(
                    new EpisodeParticipant(episode, ParticipantType.CAST, character, character.getName()));
        }

        return EpisodeResponse.from(episode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantResponse> getParticipants(Long memberId, Long episodeId) {
        Episode episode = getEpisodeOrThrow(memberId, episodeId);
        return episodeParticipantRepository.findAllByEpisode_EpisodeId(episode.getEpisodeId()).stream()
                .map(ParticipantResponse::from)
                .collect(Collectors.toList());
    }

    private Episode getEpisodeOrThrow(Long memberId, Long episodeId) {
        return episodeRepository.findByEpisodeIdAndMemberId(episodeId, memberId)
                .orElseThrow(() -> new EpisodeNotFoundException(episodeId));
    }
}

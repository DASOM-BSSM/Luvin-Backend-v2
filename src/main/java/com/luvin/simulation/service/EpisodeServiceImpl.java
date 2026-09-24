package com.luvin.simulation.service;

import com.luvin.common.exception.EpisodeNotFoundException;
import com.luvin.common.exception.UserNotFoundException;
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
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EpisodeServiceImpl implements EpisodeService {

    private static final String MALE = "MALE";
    private static final String FEMALE = "FEMALE";

    private final EpisodeRepository episodeRepository;
    private final EpisodeParticipantRepository episodeParticipantRepository;
    private final SimulationCharacterRepository simulationCharacterRepository;
    private final AiCloneRepository aiCloneRepository;
    private final UserRepository userRepository;

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

        User user = userRepository.findById(memberId)
                .orElseThrow(() -> new UserNotFoundException(memberId));
        String userGender = user.getGender();

        // "나" 참가자: 유저의 AiClone 이름을 스냅샷으로 저장. 아직 AiClone이 없으면 기본 이름 사용.
        String selfName = aiCloneRepository.findTopByUserIdOrderByIdDesc(memberId)
                .map(AiClone::getCloneName)
                .orElse("나의 분신");
        episodeParticipantRepository.save(
                new EpisodeParticipant(episode, ParticipantType.SELF, null, selfName, userGender));

        // 고정 AI 출연진 중 로그인 사용자와 이성인 캐스팅만 참가자로 등록한다.
        // userGender가 MALE/FEMALE로 인식되지 않으면(널 등, PUT /api/users/me에 아직 gender 설정
        // 경로가 없어 발생 가능) 기존 동작대로 출연진 풀 전체를 등록해 하위 호환을 유지한다.
        List<SimulationCharacter> cast = resolveOppositeGender(userGender)
                .map(simulationCharacterRepository::findAllByGenderIgnoreCase)
                .orElseGet(simulationCharacterRepository::findAll);
        for (SimulationCharacter character : cast) {
            episodeParticipantRepository.save(new EpisodeParticipant(
                    episode, ParticipantType.CAST, character, character.getName(), character.getGender()));
        }

        return EpisodeResponse.from(episode);
    }

    private Optional<String> resolveOppositeGender(String userGender) {
        if (MALE.equalsIgnoreCase(userGender)) {
            return Optional.of(FEMALE);
        }
        if (FEMALE.equalsIgnoreCase(userGender)) {
            return Optional.of(MALE);
        }
        return Optional.empty();
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

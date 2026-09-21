package com.luvin.simulation.service;

import com.luvin.common.exception.DuplicateVoteException;
import com.luvin.common.exception.EpisodeNotFoundException;
import com.luvin.common.exception.ParticipantNotFoundException;
import com.luvin.simulation.domain.Episode;
import com.luvin.simulation.domain.EpisodeParticipant;
import com.luvin.simulation.domain.Match;
import com.luvin.simulation.domain.VoteResultEntry;
import com.luvin.simulation.dto.ParticipantResponse;
import com.luvin.simulation.dto.VoteRequest;
import com.luvin.simulation.dto.VoteResultData;
import com.luvin.simulation.repository.EpisodeParticipantRepository;
import com.luvin.simulation.repository.EpisodeRepository;
import com.luvin.simulation.repository.EpisodeVoteRepository;
import com.luvin.simulation.repository.MatchRepository;
import com.luvin.simulation.repository.VoteResultEntryRepository;
import com.luvin.simulation.domain.EpisodeVote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoteServiceImpl implements VoteService {

    private final EpisodeRepository episodeRepository;
    private final EpisodeParticipantRepository episodeParticipantRepository;
    private final EpisodeVoteRepository episodeVoteRepository;
    private final VoteResultEntryRepository voteResultEntryRepository;
    private final MatchRepository matchRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantResponse> getVoteOptions(Long memberId, Long episodeId) {
        Episode episode = getEpisodeOrThrow(memberId, episodeId);
        return episodeParticipantRepository.findAllByEpisode_EpisodeId(episode.getEpisodeId()).stream()
                .map(ParticipantResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void vote(Long memberId, Long episodeId, VoteRequest request) {
        Episode episode = getEpisodeOrThrow(memberId, episodeId);
        if (episodeVoteRepository.existsByEpisode_EpisodeId(episode.getEpisodeId())) {
            throw new DuplicateVoteException(episodeId);
        }
        EpisodeParticipant selected = getParticipantOrThrow(episode, request.getSelectedParticipantId());
        episodeVoteRepository.save(new EpisodeVote(episode, selected));
    }

    @Override
    @Transactional(readOnly = true)
    public VoteResultData getVoteResults(Long memberId, Long episodeId) {
        Episode episode = getEpisodeOrThrow(memberId, episodeId);

        List<VoteResultData.PickItem> picks = voteResultEntryRepository
                .findAllByEpisode_EpisodeId(episode.getEpisodeId()).stream()
                .map(e -> new VoteResultData.PickItem(
                        e.getParticipant().getParticipantId(), e.getVotedForParticipant().getParticipantId()))
                .collect(Collectors.toList());

        List<VoteResultData.MatchItem> matches = matchRepository
                .findAllByEpisode_EpisodeId(episode.getEpisodeId()).stream()
                .map(m -> new VoteResultData.MatchItem(
                        m.getParticipantA().getParticipantId(), m.getParticipantB().getParticipantId(), m.isFinal()))
                .collect(Collectors.toList());

        return new VoteResultData(picks, matches);
    }

    @Override
    @Transactional
    public void saveVoteResults(Long memberId, Long episodeId, VoteResultData request) {
        Episode episode = getEpisodeOrThrow(memberId, episodeId);

        if (request.getPicks() != null) {
            for (VoteResultData.PickItem pick : request.getPicks()) {
                EpisodeParticipant participant = getParticipantOrThrow(episode, pick.getParticipantId());
                EpisodeParticipant votedFor = getParticipantOrThrow(episode, pick.getVotedForParticipantId());
                voteResultEntryRepository.save(new VoteResultEntry(episode, participant, votedFor));
            }
        }

        if (request.getMatches() != null) {
            for (VoteResultData.MatchItem matchItem : request.getMatches()) {
                EpisodeParticipant a = getParticipantOrThrow(episode, matchItem.getParticipantAId());
                EpisodeParticipant b = getParticipantOrThrow(episode, matchItem.getParticipantBId());
                matchRepository.save(new Match(episode, a, b, matchItem.getIsFinal()));
            }
        }
    }

    private EpisodeParticipant getParticipantOrThrow(Episode episode, Long participantId) {
        return episodeParticipantRepository
                .findByParticipantIdAndEpisode_EpisodeId(participantId, episode.getEpisodeId())
                .orElseThrow(() -> new ParticipantNotFoundException(participantId));
    }

    private Episode getEpisodeOrThrow(Long memberId, Long episodeId) {
        return episodeRepository.findByEpisodeIdAndMemberId(episodeId, memberId)
                .orElseThrow(() -> new EpisodeNotFoundException(episodeId));
    }
}

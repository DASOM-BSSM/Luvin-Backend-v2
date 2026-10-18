package com.luvin.simulation.service;

import com.luvin.common.exception.EpisodeNotFoundException;
import com.luvin.common.exception.VoteReasonNotFoundException;
import com.luvin.simulation.dto.VoteReasonResponse;
import com.luvin.simulation.repository.EpisodeRepository;
import com.luvin.simulation.repository.VoteReasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoteReasonServiceImpl implements VoteReasonService {

    private final EpisodeRepository episodeRepository;
    private final VoteReasonRepository voteReasonRepository;

    @Override
    @Transactional(readOnly = true)
    public VoteReasonResponse getVoteReason(Long memberId, Long episodeId) {
        episodeRepository.findByEpisodeIdAndMemberId(episodeId, memberId)
                .orElseThrow(() -> new EpisodeNotFoundException(episodeId));
        // TODO: FastAPI 투표 결과 연동 시 근거 저장 경로 추가 — 그 전까지는 저장된 근거가 없어 404를 반환한다.
        return voteReasonRepository.findByEpisode_EpisodeId(episodeId)
                .map(VoteReasonResponse::from)
                .orElseThrow(() -> new VoteReasonNotFoundException(episodeId));
    }
}

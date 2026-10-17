package com.luvin.simulation.service;

import com.luvin.simulation.dto.LikeMessageResponse;

import java.util.List;

public interface MessageLikeService {
    void likeMessage(Long memberId, Long matchId, Long messageId);

    List<LikeMessageResponse> getLikedMessages(Long memberId);
}

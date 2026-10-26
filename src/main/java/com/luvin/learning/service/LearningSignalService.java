package com.luvin.learning.service;

import com.luvin.learning.domain.LearningSourceType;
import com.luvin.learning.repository.UserLearningSignalRepository;
import com.luvin.learning.repository.UserPersonalityAdjustmentRepository;
import com.luvin.learning.repository.UserSpeechStyleRepository;
import com.luvin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 쓴 글을 학습 신호로 남기고(원본 저장 트랜잭션 안에서), 원본이 지워지면 신호도 지운다.
 * 신호는 "내가 쓴 것"만 가리킨다 — 호출부는 항상 그 글의 작성자 id를 넘긴다.
 * 학습 동의가 꺼져 있으면 신호를 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class LearningSignalService {

    private final UserLearningSignalRepository signalRepository;
    private final UserSpeechStyleRepository speechStyleRepository;
    private final UserPersonalityAdjustmentRepository personalityAdjustmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public void record(Long authorId, LearningSourceType type, Long sourceId) {
        if (userRepository.findLearningEnabled(authorId).orElse(false)) {
            signalRepository.insertIfAbsent(authorId, type.name(), sourceId);
        }
    }

    @Transactional
    public void remove(LearningSourceType type, Long sourceId) {
        signalRepository.deleteBySource(type.name(), sourceId);
    }

    /** 일기 삭제 전에 호출: 일기와 거기 달린 댓글의 신호. */
    @Transactional
    public void removeForDiary(Long diaryId) {
        signalRepository.deleteForDiary(diaryId);
    }

    /** 공유방 삭제 전에 호출: 방의 일기·댓글·그룹 답 신호. */
    @Transactional
    public void removeForRoom(Long roomId) {
        signalRepository.deleteForRoom(roomId);
    }

    /** 회원 탈퇴: 신호와 학습 결과를 모두 지운다. */
    @Transactional
    public void removeAllForUser(Long userId) {
        signalRepository.deleteByUserId(userId);
        speechStyleRepository.deleteById(userId);
        personalityAdjustmentRepository.deleteById(userId);
    }
}

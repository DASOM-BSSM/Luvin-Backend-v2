package com.luvin.user.service;

import com.luvin.learning.service.LearningSignalService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 탈퇴할 때 users 행 밖에 흩어진 내 기록을 지운다(개인정보처리방침의 "탈퇴 시 모두 삭제"). 대부분의 도메인이 user_id를
 * FK 없는 plain 컬럼으로 들고 있어 users를 지워도 남기 때문이다.
 *
 * - 지우는 것: 성향 테스트 응답·결과, 밸런스 게임 응답, 남의 방에 쓴 일기(딸린 댓글·반응 포함)와 어디서든 쓴 댓글·이모지 반응·
 *   그룹 질문 답, 미니게임 기록, 토큰·토큰 내역, AI 요청 멱등 키, 기존 시뮬레이션(5화 흐름) 기록
 * - 러빈지옥 시즌·내가 만든 공유방·학습 신호·AI 분신·분석 결과·Apple 토큰은 호출부가 각자의 서비스로 지운다.
 * - 그룹 질문(room_question)은 방 전체의 질문이라 남긴다. 만든 사람(requested_by)만 탈퇴한 id로 남고, 조회 때 creator가 null이다.
 */
@Component
@RequiredArgsConstructor
public class UserDataEraser {

    /** 기존 시뮬레이션 회차(episodes)에 딸린 테이블. FK 순서대로(참조하는 쪽 먼저) 지운다. */
    static final List<String> LEGACY_EPISODE_TABLES = List.of(
            "action_feedbacks", "message_likes", "one_on_one_messages", "matches", "group_conversation_messages",
            "episode_votes", "vote_result_entries", "vote_reasons", "episode_game_results", "highlights",
            "simulation_notes", "episode_participants");

    /** member_id로 바로 지우는 테이블. */
    static final List<String> MEMBER_TABLES = List.of(
            "survey_answers", "daily_question_answers", "minigame_play_results", "user_tokens", "token_histories",
            "ai_idempotency_keys");

    private final LearningSignalService learningSignalService;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void eraseAll(Long userId) {
        eraseDiaryActivity(userId);
        eraseSurveys(userId);
        for (String table : MEMBER_TABLES) {
            delete("DELETE FROM " + table + " WHERE member_id = :userId", userId);
        }
        eraseLegacySimulation(userId);
    }

    /** 남의 방에 쓴 내 일기(다른 사람의 댓글·반응과 그 학습 신호까지), 내 댓글·반응·그룹 질문 답. */
    private void eraseDiaryActivity(Long userId) {
        @SuppressWarnings("unchecked")
        List<Number> diaryIds = entityManager.createNativeQuery("SELECT id FROM diary WHERE user_id = :userId")
                .setParameter("userId", userId)
                .getResultList();
        for (Number diaryId : diaryIds) {
            learningSignalService.removeForDiary(diaryId.longValue());
        }
        delete("DELETE FROM diary_reaction WHERE user_id = :userId "
                + "OR diary_id IN (SELECT id FROM diary WHERE user_id = :userId)", userId);
        delete("DELETE FROM diary_comment WHERE user_id = :userId "
                + "OR diary_id IN (SELECT id FROM diary WHERE user_id = :userId)", userId);
        delete("DELETE FROM diary WHERE user_id = :userId", userId);
        delete("DELETE FROM room_question_answer WHERE user_id = :userId", userId);
    }

    private void eraseSurveys(Long userId) {
        String mySubmissions = "(SELECT id FROM survey_submissions WHERE member_id = :userId)";
        delete("DELETE FROM survey_results WHERE submission_id IN " + mySubmissions, userId);
        delete("DELETE FROM survey_submission_answers WHERE submission_id IN " + mySubmissions, userId);
        delete("DELETE FROM survey_submissions WHERE member_id = :userId", userId);
    }

    private void eraseLegacySimulation(Long userId) {
        String myEpisodes = "(SELECT episode_id FROM episodes WHERE member_id = :userId)";
        delete("DELETE FROM vote_reason_based_on WHERE reason_id IN "
                + "(SELECT reason_id FROM vote_reasons WHERE episode_id IN " + myEpisodes + ")", userId);
        for (String table : LEGACY_EPISODE_TABLES) {
            if (table.equals("one_on_one_messages")) {
                delete("DELETE FROM one_on_one_messages WHERE match_id IN "
                        + "(SELECT match_id FROM matches WHERE episode_id IN " + myEpisodes + ")", userId);
            } else {
                delete("DELETE FROM " + table + " WHERE episode_id IN " + myEpisodes, userId);
            }
        }
        delete("DELETE FROM episodes WHERE member_id = :userId", userId);
        String myReports = "(SELECT report_id FROM simulation_reports WHERE member_id = :userId)";
        delete("DELETE FROM simulation_report_strengths WHERE report_id IN " + myReports, userId);
        delete("DELETE FROM simulation_report_weaknesses WHERE report_id IN " + myReports, userId);
        delete("DELETE FROM simulation_reports WHERE member_id = :userId", userId);
    }

    private void delete(String sql, Long userId) {
        entityManager.createNativeQuery(sql).setParameter("userId", userId).executeUpdate();
    }
}

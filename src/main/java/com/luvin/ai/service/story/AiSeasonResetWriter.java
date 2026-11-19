package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiSeason;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.token.domain.TokenHistory;
import com.luvin.token.domain.TokenHistoryType;
import com.luvin.token.domain.TokenKind;
import com.luvin.token.domain.UserToken;
import com.luvin.token.repository.TokenHistoryRepository;
import com.luvin.token.repository.UserTokenRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 시즌 초기화: 사용자의 AI 시즌을 시즌 행까지 모두 지운다. 계정·설문 결과·감정일기·공유방은 남긴다.
 *
 * - 시즌에 딸린 행은 season_id로 전부 지운다(회차 본문·선택·관계·리포트·쪽지와 시즌 편지·좋아요·피드백·열린 회차·새 시즌
 *   설정·출연진, 기존 5화 흐름 기록). 시즌 생성 입력도 지워 다음 from-survey가 최신 설문으로 새 AI 시즌을 만든다.
 * - refund면 그 시즌들에서 회차를 열며 쓴 토큰을 돌려준다(환불 내역은 REWARD로 남긴다). 개발 전용 경로만 돌려주고,
 *   정식 경로는 돌려주지 않는다 — 열람 → 초기화 → 환불을 반복해 유료 회차를 무료로 보는 것을 막는다.
 * - 지운 시즌의 row id는 다시 쓰이지 않으므로, 초기화 전에 돌던 회차 생성·완료·선택 결과는 저장할 시즌·회차를 찾지 못해
 *   결과를 버린다.
 */
@Service
@RequiredArgsConstructor
public class AiSeasonResetWriter {

    /** ai_seasons를 참조하는 테이블(season_id). 새 시즌 테이블을 추가하면 여기에도 넣는다. */
    static final List<String> SEASON_TABLES = List.of(
            "ai_season_designs", "ai_season_choices", "ai_bread_relations", "ai_episode_contents",
            "ai_season_story_reports", "partner_letters", "ai_story_feedbacks", "ai_story_message_likes",
            "ai_episode_unlocks", "ai_episode_feedbacks", "ai_episode_message_likes", "ai_selections",
            "ai_episode_progresses", "ai_season_characters");
    static final String REFUND_REASON = "러빈지옥 시즌 초기화 환불";

    private final AiSeasonRepository seasonRepository;
    private final UserTokenRepository userTokenRepository;
    private final TokenHistoryRepository tokenHistoryRepository;
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 지운 시즌 수, 돌려준 소금빵·식빵, 초기화 뒤 소금빵·식빵 잔액. 시즌이 없거나 환불하지 않으면 돌려준 양은 0이다.
     * 회차를 연 토큰은 낸 종류(paid_with, 이전 기록은 소금빵)로 돌려준다.
     */
    public record Result(int deletedSeasons, int refundedTokens, int tokenBalance, int refundedBreadTokens,
                         int breadTokenBalance) {
    }

    @Transactional
    public Result deleteAllSeasons(Long memberId, boolean refund) {
        List<AiSeason> seasons = seasonRepository.findAllByMemberIdOrderBySeasonNumberDesc(memberId);
        int refunded = 0;
        int refundedBread = 0;
        for (AiSeason season : seasons) {
            // 같은 시즌을 쓰는 다른 쓰기(출연진 배정·새 시즌 설정 반영 등)와 겹치지 않게 잠근 뒤 지운다.
            seasonRepository.findWithLockById(season.getId());
            if (refund) {
                refunded += spentOn(season.getId(), false);
                refundedBread += spentOn(season.getId(), true);
            }
            for (String table : SEASON_TABLES) {
                entityManager.createNativeQuery("DELETE FROM " + table + " WHERE season_id = :seasonPk")
                        .setParameter("seasonPk", season.getId())
                        .executeUpdate();
            }
            entityManager.createNativeQuery("DELETE FROM ai_seasons WHERE id = :seasonPk")
                    .setParameter("seasonPk", season.getId())
                    .executeUpdate();
        }
        entityManager.createNativeQuery("DELETE FROM ai_season_creation_inputs WHERE member_id = :memberId")
                .setParameter("memberId", memberId)
                .executeUpdate();
        entityManager.clear();

        refund(memberId, TokenKind.SALT, refunded);
        refund(memberId, TokenKind.BREAD, refundedBread);
        UserToken balances = userTokenRepository.findByMemberId(memberId).orElse(null);
        return new Result(seasons.size(), refunded, balances == null ? 0 : balances.getTokenBalance(),
                refundedBread, balances == null ? 0 : balances.getBreadTokenBalance());
    }

    private int spentOn(Long seasonPk, boolean bread) {
        String kind = bread ? "paid_with = 'BREAD'" : "(paid_with IS NULL OR paid_with <> 'BREAD')";
        return ((Number) entityManager.createNativeQuery(
                        "SELECT COALESCE(SUM(tokens_spent), 0) FROM ai_episode_unlocks WHERE season_id = :seasonPk AND "
                                + kind)
                .setParameter("seasonPk", seasonPk)
                .getSingleResult()).intValue();
    }

    private void refund(Long memberId, TokenKind kind, int amount) {
        if (amount <= 0) {
            return;
        }
        UserToken userToken = userTokenRepository.findByMemberId(memberId)
                .orElseGet(() -> userTokenRepository.save(new UserToken(memberId, 0, 0)));
        userToken.earn(kind, amount);
        tokenHistoryRepository.save(new TokenHistory(memberId, TokenHistoryType.REWARD, amount,
                kind == TokenKind.BREAD ? REFUND_REASON + "(식빵)" : REFUND_REASON));
    }
}

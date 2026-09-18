package com.luvin.analysis.service;

import com.luvin.analysis.domain.AnalysisResult;
import com.luvin.analysis.dto.AiCloneResponse;
import com.luvin.analysis.dto.AnalysisChatRequest;
import com.luvin.analysis.dto.AnalysisChatResponse;
import com.luvin.analysis.dto.AnalysisReportResponse;
import com.luvin.analysis.dto.AnalysisSummaryResponse;
import com.luvin.analysis.repository.AnalysisResultRepository;
import com.luvin.common.exception.BusinessException;
import com.luvin.common.exception.ErrorCode;
import com.luvin.common.security.SecurityUtils;
import com.luvin.dailyquestion.repository.DailyQuestionAnswerRepository;
import com.luvin.simulation.domain.AiClone;
import com.luvin.simulation.repository.AiCloneRepository;
import com.luvin.survey.repository.SurveyAnswerRepository;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisResultRepository analysisResultRepository;
    private final UserRepository userRepository;
    private final AiCloneRepository aiCloneRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final DailyQuestionAnswerRepository dailyQuestionAnswerRepository;

    public AnalysisSummaryResponse getSummary() {
        AnalysisResult result = getOrCreateLatestResult();
        return new AnalysisSummaryResponse(result.getTitle(), result.getDatingStyle(), result.getDescription());
    }

    @Transactional
    public AnalysisSummaryResponse generateAnalysis() {
        AnalysisResult result = createAnalysis(getCurrentUser());
        return new AnalysisSummaryResponse(result.getTitle(), result.getDatingStyle(), result.getDescription());
    }

    public AnalysisReportResponse getReport() {
        User user = getCurrentUser();
        AnalysisResult result = getOrCreateLatestResult();
        return new AnalysisReportResponse(
                user.getNickname(),
                result.getSummary(),
                result.getStrengths(),
                result.getCautions()
        );
    }

    @Transactional
    public AiCloneResponse getClone() {
        User user = getCurrentUser();
        AnalysisResult result = getOrCreateLatestResult();
        AiClone clone = aiCloneRepository.findTopByUserIdOrderByIdDesc(user.getId())
                .orElseGet(() -> aiCloneRepository.save(AiClone.builder()
                        .user(user)
                        .cloneName(user.getNickname() + " Clone")
                        .speakingStyle(result.getExpressionScore() >= 60 ? "직설적이고 적극적" : "차분하고 공감형")
                        .datingStyle(result.getDatingStyle())
                        .personaSummary(result.getSummary())
                        .build()));
        return new AiCloneResponse(clone.getId(), clone.getCloneName(), clone.getSpeakingStyle(), clone.getDatingStyle());
    }

    public AnalysisChatResponse chat(AnalysisChatRequest request) {
        AnalysisResult result = getOrCreateLatestResult();
        String reply = result.getConflictResolutionScore() >= 60
                ? "지금은 대화를 피하기보다 차분하게 핵심 감정을 먼저 말해보는 편이 좋아 보여요."
                : "지금은 바로 결론을 내기보다 한 템포 쉬고 상황을 정리한 뒤 이야기해보는 편이 좋아 보여요.";
        return new AnalysisChatResponse(request.message(), reply);
    }

    private AnalysisResult getOrCreateLatestResult() {
        User user = getCurrentUser();
        return analysisResultRepository.findTopByUserIdOrderByIdDesc(user.getId())
                .orElseGet(() -> createAnalysis(user));
    }

    @Transactional
    protected AnalysisResult createAnalysis(User user) {
        int expressionScore = calculateExpressionScore(user);
        int stabilityScore = calculateStabilityScore(user);
        int conflictScore = calculateConflictScore(user);

        String style = decideDatingStyle(expressionScore, stabilityScore, conflictScore, user);
        String description = style + " 성향으로, 감정 표현과 안정감의 균형을 중요하게 보는 편입니다.";
        String summary = "표현 점수 " + expressionScore + ", 안정감 점수 " + stabilityScore + ", 갈등 해결 점수 " + conflictScore
                + "를 기반으로 " + style + " 유형으로 분석되었습니다.";

        AnalysisResult result = AnalysisResult.builder()
                .user(user)
                .title("나의 연애 성향 분석")
                .datingStyle(style)
                .description(description)
                .summary(summary)
                .expressionScore(expressionScore)
                .stabilityScore(stabilityScore)
                .conflictResolutionScore(conflictScore)
                .strengths(List.of(
                        "상대의 분위기를 읽고 대응하려는 편입니다.",
                        "관계의 안정감을 유지하려는 의지가 강합니다."
                ))
                .cautions(List.of(
                        expressionScore < 50 ? "감정 표현이 부족해 오해를 줄 수 있습니다." : "감정 표현이 빠를 때 속도 차이가 생길 수 있습니다.",
                        conflictScore < 50 ? "갈등 상황에서 회피적으로 보일 수 있습니다." : "갈등 상황에서 결론을 서두를 수 있습니다."
                ))
                .build();

        return analysisResultRepository.save(result);
    }

    private User getCurrentUser() {
        return userRepository.findById(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private int calculateExpressionScore(User user) {
        int score = 50;
        if (user.getMbti() != null && (user.getMbti().startsWith("E") || user.getMbti().contains("F"))) {
            score += 20;
        }
        if (user.getNickname() != null && user.getNickname().length() >= 6) {
            score += 5;
        }
        score += Math.min(dailyQuestionAnswerRepository.countByMemberId(user.getId()) * 2, 10);
        return Math.min(score, 95);
    }

    private int calculateStabilityScore(User user) {
        int score = 55;
        if ("신중형".equals(user.getDatingStyle())) {
            score += 20;
        }
        if (user.getMbti() != null && user.getMbti().contains("J")) {
            score += 10;
        }
        score += Math.min(surveyAnswerRepository.countByMemberId(user.getId()), 10);
        return Math.min(score, 95);
    }

    private int calculateConflictScore(User user) {
        int score = 45;
        if ("신중형".equals(user.getDatingStyle())) {
            score += 15;
        }
        if (user.getMbti() != null && user.getMbti().contains("T")) {
            score += 10;
        } else if (user.getMbti() != null && user.getMbti().contains("F")) {
            score += 15;
        }
        score += Math.min(dailyQuestionAnswerRepository.countByMemberId(user.getId()), 10);
        return Math.min(score, 95);
    }

    private String decideDatingStyle(int expressionScore, int stabilityScore, int conflictScore, User user) {
        if (stabilityScore >= 70 && conflictScore >= 60) {
            return "안정형";
        }
        if (expressionScore >= 70) {
            return "직진형";
        }
        if ("신중형".equals(user.getDatingStyle())) {
            return "신중형";
        }
        return "균형형";
    }
}

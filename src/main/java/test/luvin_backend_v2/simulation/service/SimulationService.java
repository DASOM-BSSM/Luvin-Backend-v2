package com.luvin.simulation.service;

import com.luvin.analysis.domain.AnalysisResult;
import com.luvin.analysis.repository.AnalysisResultRepository;
import com.luvin.common.exception.BusinessException;
import com.luvin.common.exception.ErrorCode;
import com.luvin.common.security.SecurityUtils;
import com.luvin.simulation.SimulationStatus;
import com.luvin.simulation.domain.AiClone;
import com.luvin.simulation.domain.Simulation;
import com.luvin.simulation.domain.SimulationAction;
import com.luvin.simulation.domain.SimulationIntervention;
import com.luvin.simulation.dto.AiCloneCreateRequest;
import com.luvin.simulation.dto.AiCloneDetailResponse;
import com.luvin.simulation.dto.SimulationActionResponse;
import com.luvin.simulation.dto.SimulationCreateRequest;
import com.luvin.simulation.dto.SimulationFinalCoupleResponse;
import com.luvin.simulation.dto.SimulationInterventionRequest;
import com.luvin.simulation.dto.SimulationMatchingResponse;
import com.luvin.simulation.dto.SimulationReportResponse;
import com.luvin.simulation.dto.SimulationStatusResponse;
import com.luvin.simulation.repository.AiCloneRepository;
import com.luvin.simulation.repository.SimulationActionRepository;
import com.luvin.simulation.repository.SimulationInterventionRepository;
import com.luvin.simulation.repository.SimulationRepository;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private final UserRepository userRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiCloneRepository aiCloneRepository;
    private final SimulationRepository simulationRepository;
    private final SimulationActionRepository simulationActionRepository;
    private final SimulationInterventionRepository simulationInterventionRepository;

    @Transactional
    public AiCloneDetailResponse createClone(AiCloneCreateRequest request) {
        User user = getCurrentUser();
        AnalysisResult result = analysisResultRepository.findTopByUserIdOrderByIdDesc(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        AiClone clone = aiCloneRepository.save(AiClone.builder()
                .user(user)
                .cloneName(request.cloneName())
                .speakingStyle(result.getExpressionScore() >= 60 ? "직설적이고 적극적" : "차분하고 공감형")
                .datingStyle(result.getDatingStyle())
                .personaSummary(result.getSummary())
                .build());

        return toCloneResponse(clone);
    }

    public AiCloneDetailResponse getLatestClone() {
        AiClone clone = aiCloneRepository.findTopByUserIdOrderByIdDesc(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return toCloneResponse(clone);
    }

    @Transactional
    public SimulationStatusResponse createSimulation(SimulationCreateRequest request) {
        User user = getCurrentUser();
        AiClone clone = aiCloneRepository.findByIdAndUserId(request.cloneId(), user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        Simulation simulation = simulationRepository.save(Simulation.builder()
                .user(user)
                .aiClone(clone)
                .title(request.title() == null || request.title().isBlank() ? clone.getCloneName() + " 시뮬레이션" : request.title())
                .status(SimulationStatus.CREATED)
                .actionCount(0)
                .interventionCount(0)
                .currentSummary("시뮬레이션이 생성되었고 첫 장면을 준비 중입니다.")
                .build());

        return toStatusResponse(simulation);
    }

    public SimulationStatusResponse getStatus(Long simulationId) {
        return toStatusResponse(getSimulation(simulationId));
    }

    @Transactional
    public SimulationStatusResponse intervene(Long simulationId, SimulationInterventionRequest request) {
        Simulation simulation = getSimulation(simulationId);
        simulationInterventionRepository.save(SimulationIntervention.builder()
                .simulation(simulation)
                .message(request.message())
                .build());
        simulation.applyIntervention("사용자 개입 반영: " + request.message());
        return toStatusResponse(simulation);
    }

    @Transactional
    public SimulationActionResponse generateAction(Long simulationId) {
        Simulation simulation = getSimulation(simulationId);
        int nextRound = simulation.getActionCount() + 1;
        String title = "Round " + nextRound + " 행동 생성";
        String detail = buildActionDetail(simulation, nextRound);

        SimulationAction action = simulationActionRepository.save(SimulationAction.builder()
                .simulation(simulation)
                .roundNumber(nextRound)
                .title(title)
                .detail(detail)
                .build());

        simulation.incrementAction(detail);
        if (nextRound >= 3) {
            finalizeSimulation(simulation);
        }

        return new SimulationActionResponse(action.getId(), action.getRoundNumber(), action.getTitle(), action.getDetail());
    }

    @Transactional
    public SimulationReportResponse getReport(Long simulationId) {
        Simulation simulation = getSimulation(simulationId);
        if (simulation.getReportSummary() == null) {
            finalizeSimulation(simulation);
        }
        return new SimulationReportResponse(simulation.getId(), simulation.getReportSummary());
    }

    @Transactional
    public SimulationMatchingResponse getMatching(Long simulationId) {
        Simulation simulation = getSimulation(simulationId);
        if (simulation.getMatchingSummary() == null) {
            finalizeSimulation(simulation);
        }
        return new SimulationMatchingResponse(simulation.getId(), simulation.getMatchingSummary());
    }

    @Transactional
    public SimulationFinalCoupleResponse getFinalCouple(Long simulationId) {
        Simulation simulation = getSimulation(simulationId);
        if (simulation.getFinalCoupleSummary() == null) {
            finalizeSimulation(simulation);
        }
        return new SimulationFinalCoupleResponse(simulation.getId(), simulation.getFinalCoupleSummary());
    }

    private void finalizeSimulation(Simulation simulation) {
        String report = simulation.getAiClone().getCloneName() + "은(는) 감정 표현보다 신뢰 축적을 우선하는 방향으로 관계를 이끌었습니다.";
        String matching = simulation.getAiClone().getCloneName() + "과 궁합이 높은 상대는 '안정감을 중시하는 타입'으로 분석되었습니다.";
        String finalCouple = simulation.getAiClone().getCloneName() + " + 안정형 파트너";
        simulation.complete(report, matching, finalCouple);
    }

    private String buildActionDetail(Simulation simulation, int nextRound) {
        return switch (nextRound) {
            case 1 -> simulation.getAiClone().getCloneName() + "이(가) 첫 대화에서 조심스럽게 호감 신호를 보냈습니다.";
            case 2 -> "상대의 반응 속도를 관찰하며 관계 템포를 조절했습니다.";
            default -> "갈등 상황에서 감정 정리 후 대화를 시도하며 안정적인 방향으로 관계를 회복했습니다.";
        };
    }

    private Simulation getSimulation(Long simulationId) {
        return simulationRepository.findByIdAndUserId(simulationId, SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private User getCurrentUser() {
        return userRepository.findById(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private AiCloneDetailResponse toCloneResponse(AiClone clone) {
        return new AiCloneDetailResponse(
                clone.getId(),
                clone.getCloneName(),
                clone.getSpeakingStyle(),
                clone.getDatingStyle(),
                clone.getPersonaSummary()
        );
    }

    private SimulationStatusResponse toStatusResponse(Simulation simulation) {
        return new SimulationStatusResponse(
                simulation.getId(),
                simulation.getTitle(),
                simulation.getStatus(),
                simulation.getActionCount(),
                simulation.getInterventionCount(),
                simulation.getCurrentSummary()
        );
    }
}

package com.luvin.simulation.controller;

import com.luvin.common.response.ApiResponse;
import com.luvin.simulation.dto.*;
import com.luvin.simulation.service.SimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/clones")
    public ApiResponse<AiCloneDetailResponse> createClone(@Valid @RequestBody AiCloneCreateRequest request) {
        return ApiResponse.ok(simulationService.createClone(request));
    }

    @GetMapping("/clones/latest")
    public ApiResponse<AiCloneDetailResponse> getLatestClone() {
        return ApiResponse.ok(simulationService.getLatestClone());
    }

    @PostMapping
    public ApiResponse<SimulationStatusResponse> createSimulation(@Valid @RequestBody SimulationCreateRequest request) {
        return ApiResponse.ok(simulationService.createSimulation(request));
    }

    @GetMapping("/{simulationId}")
    public ApiResponse<SimulationStatusResponse> getStatus(@PathVariable Long simulationId) {
        return ApiResponse.ok(simulationService.getStatus(simulationId));
    }

    @PostMapping("/{simulationId}/actions")
    public ApiResponse<SimulationActionResponse> generateAction(@PathVariable Long simulationId) {
        return ApiResponse.ok(simulationService.generateAction(simulationId));
    }

    @PostMapping("/{simulationId}/interventions")
    public ApiResponse<SimulationStatusResponse> intervene(
            @PathVariable Long simulationId,
            @Valid @RequestBody SimulationInterventionRequest request
    ) {
        return ApiResponse.ok(simulationService.intervene(simulationId, request));
    }

    @GetMapping("/{simulationId}/report")
    public ApiResponse<SimulationReportResponse> getReport(@PathVariable Long simulationId) {
        return ApiResponse.ok(simulationService.getReport(simulationId));
    }

    @GetMapping("/{simulationId}/matching")
    public ApiResponse<SimulationMatchingResponse> getMatching(@PathVariable Long simulationId) {
        return ApiResponse.ok(simulationService.getMatching(simulationId));
    }

    @GetMapping("/{simulationId}/final-couple")
    public ApiResponse<SimulationFinalCoupleResponse> getFinalCouple(@PathVariable Long simulationId) {
        return ApiResponse.ok(simulationService.getFinalCouple(simulationId));
    }
}

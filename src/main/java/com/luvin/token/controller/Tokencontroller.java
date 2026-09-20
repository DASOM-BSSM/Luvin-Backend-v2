package com.luvin.token.controller;

import com.luvin.common.security.SecurityUtils;
import com.luvin.token.dto.TokenBalanceResponse;
import com.luvin.token.dto.TokenHistoryListResponse;
import com.luvin.token.dto.TokenUseRequest;
import com.luvin.token.dto.TokenUseResponse;
import com.luvin.token.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/me")
    public TokenBalanceResponse getMyTokenStatus() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return tokenService.getMyTokenStatus(memberId);
    }

    @PostMapping("/use")
    public TokenUseResponse useToken(@Valid @RequestBody TokenUseRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return tokenService.useToken(memberId, request);
    }

    @GetMapping("/history")
    public TokenHistoryListResponse getHistory() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return tokenService.getHistory(memberId);
    }
}
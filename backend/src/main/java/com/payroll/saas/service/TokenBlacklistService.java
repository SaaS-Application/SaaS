package com.payroll.saas.service;

import com.payroll.saas.entity.TokenBlacklist;
import com.payroll.saas.repository.TokenBlacklistRepository;
import com.payroll.saas.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final JwtService jwtService;

    public void blacklist(String token) {
        if (token == null || token.isBlank() || jwtService.isTokenExpired(token)) {
            return;
        }

        tokenBlacklistRepository.findByToken(token)
                .orElseGet(() -> tokenBlacklistRepository.save(TokenBlacklist.builder()
                        .token(token)
                        .expiresAt(LocalDateTime.ofInstant(jwtService.extractExpiration(token).toInstant(), java.time.ZoneId.systemDefault()))
                        .createdAt(LocalDateTime.now())
                        .build()));
    }

    public boolean isBlacklisted(String token) {
        return token != null && !token.isBlank() && tokenBlacklistRepository.existsByToken(token);
    }
}

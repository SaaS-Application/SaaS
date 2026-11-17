package com.payroll.saas.service;

import com.payroll.saas.dto.AuthResponse;
import com.payroll.saas.dto.LoginRequest;
import com.payroll.saas.entity.PasswordResetToken;
import com.payroll.saas.entity.User;
import com.payroll.saas.entity.UserRole;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.PasswordResetTokenRepository;
import com.payroll.saas.repository.UserRepository;
import com.payroll.saas.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${app.super-admin.username:superadmin}")
    private String superAdminUsername;

    @Value("${app.super-admin.email:superadmin@payroll.local}")
    private String superAdminEmail;

    @Value("${app.super-admin.password:ChangeMe!StrongPass123}")
    private String superAdminPassword;

    @Transactional
    public void bootstrapSuperAdmin() {
        if (userRepository.findByRole(UserRole.SUPER_ADMIN).isPresent()) {
            return;
        }

        User user = User.builder()
                .username(superAdminUsername)
                .email(superAdminEmail)
                .password(passwordEncoder.encode(superAdminPassword))
                .role(UserRole.SUPER_ADMIN)
                .tenantId(0L)
                .enabled(true)
                .accountNonLocked(true)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        auditLogService.record("SUPER_ADMIN_BOOTSTRAP", user.getUsername(), user.getRole().name(), user.getTenantId(), null,
                "Initial super admin account created");
    }

    public AuthResponse login(LoginRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ApiException("Email is required");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ApiException("Password is required");
        }

        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new ApiException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException("Invalid email or password");
        }

        if (!user.isEnabled()) {
            throw new ApiException("Account is deactivated");
        }

        if (!user.isAccountNonLocked()) {
            throw new ApiException("Account is locked");
        }

        if (user.getCompany() != null && "SUSPENDED".equalsIgnoreCase(user.getCompany().getStatus())) {
            throw new ApiException("Company account is suspended");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), request.getPassword())
        );

        if (!authentication.isAuthenticated()) {
            throw new ApiException("Authentication failed");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user);
        Long companyId = user.getCompany() == null ? null : user.getCompany().getId();
        return new AuthResponse(token, user.getUsername(), user.getRole().name(), user.getTenantId(), companyId);
    }

    public String initiatePasswordReset(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElse(null);

        if (user == null) {
            return UUID.randomUUID().toString();
        }

        String token = UUID.randomUUID().toString();
        passwordResetTokenRepository.deleteByUserId(user.getId());

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .createdAt(LocalDateTime.now())
                .build();

        passwordResetTokenRepository.save(resetToken);
        user.setResetToken(token);
        user.setResetTokenExpiry(resetToken.getExpiresAt());
        userRepository.save(user);
        return token;
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new ApiException("Reset token is required");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new ApiException("Invalid or expired reset token"));

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(resetToken);
            throw new ApiException("Reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        passwordResetTokenRepository.delete(resetToken);
        auditLogService.record("PASSWORD_RESET", user.getUsername(), user.getRole().name(), user.getTenantId(), user.getCompany() == null ? null : user.getCompany().getId(), "User reset password");
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ApiException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        auditLogService.record("PASSWORD_CHANGED", user.getUsername(), user.getRole().name(), user.getTenantId(), user.getCompany() == null ? null : user.getCompany().getId(), "User changed password");
    }

    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }

        String token = authorizationHeader.substring(7).trim();
        tokenBlacklistService.blacklist(token);
    }
}

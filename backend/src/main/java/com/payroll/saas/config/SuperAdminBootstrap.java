package com.payroll.saas.config;

import com.payroll.saas.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {

    private final AuthService authService;

    @Override
    public void run(ApplicationArguments args) {
        authService.bootstrapSuperAdmin();
    }
}

package com.payroll.saas.service;

import com.payroll.saas.entity.AuditLog;
import com.payroll.saas.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void record(String action, String actor, String actorRole, Long tenantId, Long companyId, String details) {
        auditLogRepository.save(AuditLog.builder()
                .action(action)
                .actor(actor)
                .actorRole(actorRole)
                .tenantId(tenantId)
                .companyId(companyId)
                .details(details)
                .createdAt(LocalDateTime.now())
                .build());
    }
}

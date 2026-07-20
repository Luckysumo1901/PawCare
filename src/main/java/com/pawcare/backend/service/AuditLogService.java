package com.pawcare.backend.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.pawcare.backend.entity.AuditLog;
import com.pawcare.backend.repository.AuditLogRepository;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String actorId, String action, String targetType, String targetId) {
        auditLogRepository.save(new AuditLog(null, actorId, action, targetType, targetId, Instant.now()));
    }
}
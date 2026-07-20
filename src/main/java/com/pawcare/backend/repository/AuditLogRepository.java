package com.pawcare.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.pawcare.backend.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {}
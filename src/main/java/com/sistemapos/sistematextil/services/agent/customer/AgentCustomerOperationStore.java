package com.sistemapos.sistematextil.services.agent.customer;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AgentCustomerOperationStore {
    Optional<OperationRow> find(String idempotencyKey);

    boolean claim(Long sessionId, String idempotencyKey, String toolName, String requestFingerprint,
            LocalDateTime now);

    void complete(String idempotencyKey, String responseJson, LocalDateTime now);

    record OperationRow(String idempotencyKey, String toolName, String status,
            String requestFingerprint, String responseJson) {
    }
}

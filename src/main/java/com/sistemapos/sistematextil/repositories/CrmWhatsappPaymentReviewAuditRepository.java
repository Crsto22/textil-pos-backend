package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReviewAudit;

public interface CrmWhatsappPaymentReviewAuditRepository
        extends JpaRepository<CrmWhatsappPaymentReviewAudit, Long> {
    Optional<CrmWhatsappPaymentReviewAudit> findByPaymentEvidence_IdPaymentEvidence(Long evidenceId);
}

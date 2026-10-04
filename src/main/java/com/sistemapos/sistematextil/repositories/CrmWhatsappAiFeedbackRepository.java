package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.CrmWhatsappAiFeedback;

public interface CrmWhatsappAiFeedbackRepository extends JpaRepository<CrmWhatsappAiFeedback, Long> {

    @EntityGraph(attributePaths = { "reviewer", "outgoingMessage" })
    Optional<CrmWhatsappAiFeedback> findByRun_IdAiRun(Long runId);
}

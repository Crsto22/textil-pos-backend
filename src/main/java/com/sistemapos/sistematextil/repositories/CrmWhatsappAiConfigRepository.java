package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;

public interface CrmWhatsappAiConfigRepository extends JpaRepository<CrmWhatsappAiConfig, Long> {
    Optional<CrmWhatsappAiConfig> findByConnection_IdConnection(Long idConnection);
}

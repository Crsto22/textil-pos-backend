package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappArea;

public interface CrmWhatsappConnectionRepository extends JpaRepository<CrmWhatsappConnection, Long> {
    Optional<CrmWhatsappConnection> findByClientId(String clientId);
    Optional<CrmWhatsappConnection> findByClientIdAndEmpresa_IdEmpresa(String clientId, Integer idEmpresa);
    Optional<CrmWhatsappConnection> findByEmpresa_IdEmpresaAndArea(Integer idEmpresa, CrmWhatsappArea area);
    java.util.List<CrmWhatsappConnection> findByEmpresa_IdEmpresaOrderByAreaAsc(Integer idEmpresa);
}

package com.sistemapos.sistematextil.services.agent.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;

@Service
public class AgentCatalogScopeResolver {
    private final CrmWhatsappAgentSessionRepository sessionRepository;

    public AgentCatalogScopeResolver(CrmWhatsappAgentSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public CatalogScope resolve(Long sessionId) {
        if (sessionId == null) throw new IllegalArgumentException("La sesion del agente es obligatoria");
        CrmWhatsappAgentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("La sesion del agente no existe"));
        if (!Boolean.TRUE.equals(session.getActiveSlot())) {
            throw new IllegalStateException("La sesion del agente ya no esta activa");
        }
        CrmWhatsappConnection connection = session.getConnection();
        if (connection == null || connection.getIdConnection() == null
                || connection.getSucursal() == null || connection.getSucursal().getIdSucursal() == null) {
            throw new IllegalStateException("La conexion de WhatsApp no tiene una sucursal configurada");
        }
        return new CatalogScope(sessionId, connection.getIdConnection(), connection.getSucursal().getIdSucursal());
    }

    public record CatalogScope(Long sessionId, Long connectionId, Integer branchId) {
    }
}

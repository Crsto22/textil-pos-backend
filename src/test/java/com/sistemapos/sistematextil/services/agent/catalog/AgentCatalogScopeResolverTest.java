package com.sistemapos.sistematextil.services.agent.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;

class AgentCatalogScopeResolverTest {

    @Test
    void derivesBranchFromWhatsappConnection() {
        CrmWhatsappAgentSessionRepository repository = mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappAgentSession session = session(8L, 12L, 4, true);
        when(repository.findById(8L)).thenReturn(Optional.of(session));

        var scope = new AgentCatalogScopeResolver(repository).resolve(8L);

        assertEquals(12L, scope.connectionId());
        assertEquals(4, scope.branchId());
    }

    @Test
    void refusesAnInactiveSession() {
        CrmWhatsappAgentSessionRepository repository = mock(CrmWhatsappAgentSessionRepository.class);
        when(repository.findById(8L)).thenReturn(Optional.of(session(8L, 12L, 4, false)));

        assertThrows(IllegalStateException.class, () -> new AgentCatalogScopeResolver(repository).resolve(8L));
    }

    private CrmWhatsappAgentSession session(long sessionId, long connectionId, int branchId, boolean active) {
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(branchId);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(connectionId);
        connection.setSucursal(branch);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(sessionId);
        session.setConnection(connection);
        session.setActiveSlot(active);
        return session;
    }
}

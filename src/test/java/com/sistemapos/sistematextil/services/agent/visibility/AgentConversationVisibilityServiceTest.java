package com.sistemapos.sistematextil.services.agent.visibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.services.agent.context.AgentContextDataSource;
import com.sistemapos.sistematextil.util.usuario.Rol;

class AgentConversationVisibilityServiceTest {

    private final AgentConversationAccessGuard accessGuard = mock(AgentConversationAccessGuard.class);
    private final CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
    private final AgentContextDataSource context = mock(AgentContextDataSource.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AgentConversationVisibilityService service =
            new AgentConversationVisibilityService(accessGuard, sessions, context, jdbc);

    @Test
    void returnsAnEmptyObservableStateWithoutCreatingAnAgentSession() {
        Usuario advisor = new Usuario();
        advisor.setRol(Rol.VENTAS);
        when(sessions.findByConversation_IdConversationAndActiveSlotTrue(19L)).thenReturn(Optional.empty());
        when(sessions.findFirstByConversation_IdConversationOrderByIdAgentSessionDesc(19L)).thenReturn(Optional.empty());

        var result = service.getSummary(19L, advisor);

        assertThat(result.hasSession()).isFalse();
        assertThat(result.toolHistoryVisible()).isFalse();
        assertThat(result.cart().items()).isEmpty();
        verify(accessGuard).requireVisible(19L, advisor);
        verifyNoInteractions(context, jdbc);
    }

    @Test
    void reportsToolHistoryCapabilityOnlyForAdministrators() {
        Usuario admin = new Usuario();
        admin.setRol(Rol.ADMINISTRADOR);
        when(sessions.findByConversation_IdConversationAndActiveSlotTrue(20L)).thenReturn(Optional.empty());
        when(sessions.findFirstByConversation_IdConversationOrderByIdAgentSessionDesc(20L)).thenReturn(Optional.empty());

        var result = service.getSummary(20L, admin);

        assertThat(result.toolHistoryVisible()).isTrue();
        assertThat(result.toolCalls()).isEmpty();
    }
}

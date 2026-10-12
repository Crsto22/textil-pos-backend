package com.sistemapos.sistematextil.services.agent.runtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.services.agent.batch.AgentJobCoordinator;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.JobLease;

class AgentConversationRunnerTest {
    @Test
    void claimsAndProcessesAReadyJob() {
        AgentJobCoordinator coordinator = mock(AgentJobCoordinator.class);
        AgentConversationOrchestrator orchestrator = mock(AgentConversationOrchestrator.class);
        JobLease lease = new JobLease(1L, 2L, 3L, 1, "worker:lease", LocalDateTime.now());
        when(coordinator.claimNext()).thenReturn(Optional.of(lease), Optional.empty());

        new AgentConversationRunner(coordinator, orchestrator, 5).processReadyJobs();

        verify(orchestrator).process(lease);
    }
}

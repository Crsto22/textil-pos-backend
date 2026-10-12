package com.sistemapos.sistematextil.services.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent;

class AgentConversationScenarioCorpusTest {
    static final String RESOURCE = "/agent/conversation-scenarios.json";
    private static final Set<String> REQUIRED_COVERAGE = Set.of(
            "fragmented", "simultaneous", "typo", "missing-product", "similar-product", "combo", "stock",
            "variant-change", "expired-payment", "invalid-receipt", "duplicate-receipt", "shalom", "pickup",
            "support");

    @Test
    void regressionCorpusCoversEveryRequiredConversationRisk() throws Exception {
        List<Scenario> scenarios = loadScenarios();

        assertThat(scenarios).hasSizeGreaterThanOrEqualTo(12);
        assertThat(scenarios.stream().map(Scenario::id)).doesNotHaveDuplicates();
        assertThat(scenarios).allSatisfy(scenario -> {
            assertThat(scenario.id()).isNotBlank();
            assertThat(scenario.conversation()).contains("CLIENTE:");
            assertThat(scenario.acceptedIntents()).isNotEmpty();
            assertThat(scenario.covers()).isNotEmpty();
        });
        Set<String> covered = new HashSet<>();
        scenarios.forEach(scenario -> covered.addAll(scenario.covers()));
        assertThat(covered).containsAll(REQUIRED_COVERAGE);
    }

    static List<Scenario> loadScenarios() throws Exception {
        try (InputStream input = AgentConversationScenarioCorpusTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(input).as("corpus conversacional").isNotNull();
            return new ObjectMapper().readValue(input, new TypeReference<>() {});
        }
    }

    record Scenario(String id, List<String> covers, String conversation,
            Set<AgentDecisionIntent> acceptedIntents, String productReferenceContains,
            String promotionReferenceContains, boolean requiresHumanHelp) {}
}

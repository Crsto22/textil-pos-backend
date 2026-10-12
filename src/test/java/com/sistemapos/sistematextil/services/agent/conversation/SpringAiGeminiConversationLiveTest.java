package com.sistemapos.sistematextil.services.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.text.Normalizer;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService.CredentialMaterial;
import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision;
import com.sistemapos.sistematextil.services.agent.ai.SpringAiGeminiDecisionGateway;

@EnabledIfSystemProperty(named = "crm.agent.live-eval", matches = "true")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class SpringAiGeminiConversationLiveTest {

    @Test
    void geminiUnderstandsTheRealConversationCorpusAsStructuredDecisions() throws Exception {
        CrmWhatsappAiCredentialService credentials = mock(CrmWhatsappAiCredentialService.class);
        String model = System.getProperty("crm.agent.live-eval.model", "gemini-2.5-flash");
        when(credentials.resolve(anyLong())).thenReturn(
                new CredentialMaterial(System.getenv("GEMINI_API_KEY"), model, "LIVE_TEST"));
        SpringAiGeminiDecisionGateway gateway = new SpringAiGeminiDecisionGateway(credentials, 60000, 700);

        for (var scenario : AgentConversationScenarioCorpusTest.loadScenarios()) {
            AgentConversationDecision decision = gateway.interpret(1L, scenario.conversation()).decision();
            assertThat(scenario.acceptedIntents())
                    .as("intencion de %s", scenario.id())
                    .contains(decision.intent());
            if (scenario.productReferenceContains() != null) {
                assertThat(normalize(decision.productReference()))
                        .as("producto de %s", scenario.id())
                        .contains(normalize(scenario.productReferenceContains()));
            }
            if (scenario.promotionReferenceContains() != null) {
                assertThat(normalize(decision.promotionReference()))
                        .as("promocion de %s", scenario.id())
                        .contains(normalize(scenario.promotionReferenceContains()));
            }
            assertThat(decision.requiresHumanHelp())
                    .as("derivacion humana de %s", scenario.id())
                    .isEqualTo(scenario.requiresHumanHelp());
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}

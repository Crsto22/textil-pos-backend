package com.sistemapos.sistematextil.services.agent.film;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappFilmEvidence;

class AgentFilmEvidenceServiceTest {

    @Test
    void tellsCustomerThatShippingTeamWillReviewWithoutClaimingApproval() {
        assertThat(AgentFilmEvidenceService.MANUAL_REVIEW_MESSAGE)
                .contains("S/2", "film protector para embalaje", "área de envíos", "se encargará de revisarlo")
                .doesNotContain("confirmado", "aprobado");
    }

    @Test
    void acceptsExactTwoSolesWithoutRequiringOtherReceiptFields() {
        CrmWhatsappFilmEvidence evidence = new CrmWhatsappFilmEvidence();
        evidence.setDetectedAmount(new BigDecimal("2.00"));

        assertThat(AgentFilmEvidenceService.validateFilmAmount(evidence)).isEmpty();
    }

    @Test
    void rejectsAnyAmountDifferentFromTwoSoles() {
        CrmWhatsappFilmEvidence evidence = new CrmWhatsappFilmEvidence();
        evidence.setDetectedAmount(new BigDecimal("2.01"));

        assertThat(AgentFilmEvidenceService.validateFilmAmount(evidence))
                .containsExactly("el monto debe ser exactamente S/2.00");
    }

    @Test
    void rejectsWhenAmountCannotBeRead() {
        CrmWhatsappFilmEvidence evidence = new CrmWhatsappFilmEvidence();

        assertThat(AgentFilmEvidenceService.validateFilmAmount(evidence))
                .containsExactly("el monto debe ser exactamente S/2.00");
    }
}

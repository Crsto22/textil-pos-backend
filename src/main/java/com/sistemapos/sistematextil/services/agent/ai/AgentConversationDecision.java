package com.sistemapos.sistematextil.services.agent.ai;

import java.math.BigDecimal;
import java.util.List;

public record AgentConversationDecision(
        AgentDecisionIntent intent,
        String customerGoal,
        String productReference,
        String colorReference,
        String sizeReference,
        Integer quantity,
        String promotionReference,
        List<PromotionSelection> promotionSelections,
        AgentCartAction cartAction,
        AgentCommercialAction commercialAction,
        String paymentMethodReference,
        String paymentAccountReference,
        String customerName,
        String customerPhone,
        String deliveryMethod,
        String documentNumber,
        String shalomAgency,
        String shalomDestination,
        List<AgentShalomDataField> shalomFieldsToChange,
        AgentCatalogAvailability catalogAvailability,
        String sizeGuideComponent,
        List<BodyMeasurementSelection> bodyMeasurements,
        String fitPreference,
        int confidence,
        boolean requiresHumanHelp) {

    public AgentConversationDecision {
        promotionSelections = promotionSelections == null ? List.of()
                : promotionSelections.stream().filter(java.util.Objects::nonNull).toList();
        sizeGuideComponent = sizeGuideComponent == null ? "" : sizeGuideComponent;
        bodyMeasurements = bodyMeasurements == null ? List.of()
                : bodyMeasurements.stream().filter(java.util.Objects::nonNull).toList();
        shalomFieldsToChange = shalomFieldsToChange == null ? List.of()
                : shalomFieldsToChange.stream().filter(java.util.Objects::nonNull).distinct().toList();
        fitPreference = fitPreference == null ? "" : fitPreference;
    }

    public AgentConversationDecision withShalomAgencySearch(String agencyReference) {
        return new AgentConversationDecision(intent, customerGoal, productReference, colorReference,
                sizeReference, quantity, promotionReference, promotionSelections, cartAction,
                AgentCommercialAction.PROVIDE_SHALOM_DATA, paymentMethodReference,
                paymentAccountReference, customerName, customerPhone, deliveryMethod, documentNumber,
                agencyReference == null ? "" : agencyReference.trim(), shalomDestination, shalomFieldsToChange,
                catalogAvailability, sizeGuideComponent, bodyMeasurements, fitPreference,
                confidence, false);
    }

    public AgentConversationDecision withShalomDataEdit(List<AgentShalomDataField> fields) {
        return new AgentConversationDecision(intent, customerGoal, productReference, colorReference,
                sizeReference, quantity, promotionReference, promotionSelections, cartAction,
                AgentCommercialAction.EDIT_SHALOM_DATA, paymentMethodReference,
                paymentAccountReference, customerName, customerPhone, deliveryMethod, documentNumber,
                shalomAgency, shalomDestination, fields, catalogAvailability, sizeGuideComponent,
                bodyMeasurements, fitPreference, confidence, false);
    }

    public AgentConversationDecision withCommercialAction(AgentCommercialAction action) {
        return new AgentConversationDecision(intent, customerGoal, productReference, colorReference,
                sizeReference, quantity, promotionReference, promotionSelections, cartAction,
                action == null ? AgentCommercialAction.NONE : action, paymentMethodReference,
                paymentAccountReference, customerName, customerPhone, deliveryMethod, documentNumber,
                shalomAgency, shalomDestination, shalomFieldsToChange, catalogAvailability,
                sizeGuideComponent, bodyMeasurements, fitPreference, confidence, requiresHumanHelp);
    }

    public AgentConversationDecision(
            AgentDecisionIntent intent, String customerGoal, String productReference, String colorReference,
            String sizeReference, Integer quantity, String promotionReference,
            List<PromotionSelection> promotionSelections, AgentCartAction cartAction,
            AgentCommercialAction commercialAction, String paymentMethodReference,
            String paymentAccountReference, String customerName, String customerPhone,
            String deliveryMethod, String documentNumber, String shalomAgency, String shalomDestination,
            AgentCatalogAvailability catalogAvailability, int confidence, boolean requiresHumanHelp) {
        this(intent, customerGoal, productReference, colorReference, sizeReference, quantity,
                promotionReference, promotionSelections, cartAction, commercialAction, paymentMethodReference,
                paymentAccountReference, customerName, customerPhone, deliveryMethod, documentNumber,
                shalomAgency, shalomDestination, List.of(), catalogAvailability, "", List.of(), "",
                confidence, requiresHumanHelp);
    }

    /** Constructor compatible con decisiones creadas antes de agregar selecciones de combo. */
    public AgentConversationDecision(
            AgentDecisionIntent intent,
            String customerGoal,
            String productReference,
            String colorReference,
            String sizeReference,
            Integer quantity,
            String promotionReference,
            AgentCartAction cartAction,
            AgentCommercialAction commercialAction,
            String paymentMethodReference,
            String paymentAccountReference,
            String customerName,
            String customerPhone,
            String deliveryMethod,
            String documentNumber,
            String shalomAgency,
            String shalomDestination,
            AgentCatalogAvailability catalogAvailability,
            int confidence,
            boolean requiresHumanHelp) {
        this(intent, customerGoal, productReference, colorReference, sizeReference, quantity,
                promotionReference, List.of(), cartAction, commercialAction, paymentMethodReference,
                paymentAccountReference, customerName, customerPhone, deliveryMethod, documentNumber,
                shalomAgency, shalomDestination, List.of(), catalogAvailability, "", List.of(), "",
                confidence, requiresHumanHelp);
    }

    /** Compatibility constructor for isolated tests and stored bootstrap probes. */
    public AgentConversationDecision(
            AgentDecisionIntent intent,
            String customerGoal,
            String productReference,
            String colorReference,
            String sizeReference,
            Integer quantity,
            String promotionReference,
            AgentCartAction cartAction,
            String ignoredNextAction,
            int confidence,
            boolean requiresHumanHelp) {
        this(intent, customerGoal, productReference, colorReference, sizeReference, quantity,
                promotionReference, List.of(), cartAction,
                cartAction == AgentCartAction.ADD_PRODUCT ? AgentCommercialAction.ADD_PRODUCT
                        : cartAction == AgentCartAction.CONFIRM_CART ? AgentCommercialAction.CONFIRM_CART
                        : AgentCommercialAction.NONE,
                "", "", "", "", "", "", "", "", List.of(), AgentCatalogAvailability.ALL,
                "", List.of(), "",
                confidence, requiresHumanHelp);
    }

    public record PromotionSelection(
            String productReference,
            String colorReference,
            String sizeReference,
            Integer quantity) {
        public PromotionSelection {
            productReference = productReference == null ? "" : productReference;
            colorReference = colorReference == null ? "" : colorReference;
            sizeReference = sizeReference == null ? "" : sizeReference;
            quantity = quantity == null ? 0 : quantity;
        }
    }

    public record BodyMeasurementSelection(String name, BigDecimal value, String unit) {
        public BodyMeasurementSelection {
            name = name == null ? "" : name;
            unit = unit == null || unit.isBlank() ? "CM" : unit;
        }
    }
}

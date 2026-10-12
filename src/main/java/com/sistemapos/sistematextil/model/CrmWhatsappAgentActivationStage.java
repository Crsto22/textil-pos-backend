package com.sistemapos.sistematextil.model;

public enum CrmWhatsappAgentActivationStage {
    LOCAL_TEST,
    WHATSAPP_TEST,
    LIMITED,
    AUTOMATIC_SALES;

    public boolean allowsTransitionTo(CrmWhatsappAgentActivationStage target) {
        if (target == null || target == this) return true;
        return target.ordinal() < ordinal() || target.ordinal() == ordinal() + 1;
    }

    public boolean createsAutomaticSales() {
        return this == AUTOMATIC_SALES;
    }
}

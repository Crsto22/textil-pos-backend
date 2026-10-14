package com.sistemapos.sistematextil.services.agent.ai;

/**
 * Accion semantica solicitada por la clienta. Gemini interpreta; Java valida
 * el estado y ejecuta la operacion. Ningun valor depende de palabras exactas.
 */
public enum AgentCommercialAction {
    NONE,
    SHOW_PRODUCTS,
    SHOW_PRODUCT,
    SHOW_PRODUCT_IMAGES,
    SHOW_SIZE_GUIDE,
    RECOMMEND_SIZE,
    SHOW_PROMOTIONS,
    ADD_PRODUCT,
    CHANGE_CART,
    CONFIRM_CART,
    SHOW_PAYMENT_METHODS,
    SELECT_PAYMENT_METHOD,
    PAYMENT_TIME_REMAINING,
    RETRY_PAYMENT,
    PROVIDE_CUSTOMER_DATA,
    CONFIRM_EXISTING_CUSTOMER,
    SELECT_PICKUP,
    SELECT_SHALOM,
    PROVIDE_SHALOM_DATA,
    EDIT_SHALOM_DATA,
    CONFIRM_SHALOM_AGENCY,
    REUSE_SHALOM_PROFILE,
    HAS_PREVIOUS_PENDING_ORDER,
    NO_PREVIOUS_PENDING_ORDER,
    ACCEPT_FILM,
    DECLINE_FILM,
    SHARE_ADVISOR_CONTACT,
    SHARE_MOTORIZED_CONTACT,
    REQUEST_WHOLESALE_SUPPORT,
    REQUEST_SUPPORT
}

package com.sistemapos.sistematextil.services.agent.ai;

/**
 * Accion semantica solicitada por la clienta. Gemini interpreta; Java valida
 * el estado y ejecuta la operacion. Ningun valor depende de palabras exactas.
 */
public enum AgentCommercialAction {
    NONE,
    SHOW_PRODUCTS,
    SHOW_PRODUCT,
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
    ACCEPT_FILM,
    DECLINE_FILM,
    REQUEST_SUPPORT
}

package com.sistemapos.sistematextil.services.agent.customer;

import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.services.agent.cart.AgentCartTools;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogTools;
import com.sistemapos.sistematextil.services.agent.customer.AgentCustomerService.CustomerResult;
import com.sistemapos.sistematextil.services.agent.customer.AgentCustomerService.CustomerSearchResult;
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;

@Component
public class AgentCustomerTools {
    private final AgentCustomerService customers;

    public AgentCustomerTools(AgentCustomerService customers) {
        this.customers = customers;
    }

    @Tool(name = "buscar_cliente", description = "Busca un cliente existente por celular o documento exclusivamente en la empresa de la conexion WhatsApp. No uses nombres para decidir identidad.")
    public CustomerSearchResult buscarCliente(
            @ToolParam(description = "Celular peruano; omitir si se busca por documento", required = false) String celular,
            @ToolParam(description = "Tipo de documento: DNI, RUC o CE; puede omitirse si el numero permite inferirlo", required = false) TipoDocumento tipoDocumento,
            @ToolParam(description = "Numero de documento; omitir si se busca por celular", required = false) String numeroDocumento,
            ToolContext context) {
        return customers.search(sessionId(context), celular, tipoDocumento, numeroDocumento);
    }

    @Tool(name = "registrar_cliente", description = "Registra o reutiliza sin duplicar al cliente con nombres y celular validados, lo relaciona con la conversacion y avanza al metodo de pago solo si sus datos estan completos.")
    public CustomerResult registrarCliente(
            @ToolParam(description = "Nombres y apellidos indicados por la clienta") String nombres,
            @ToolParam(description = "Celular peruano de 9 digitos") String celular,
            @ToolParam(description = "Tipo de documento: DNI, RUC o CE; omitir si no fue solicitado", required = false) TipoDocumento tipoDocumento,
            @ToolParam(description = "Numero de documento; omitir si no fue solicitado", required = false) String numeroDocumento,
            ToolContext context) {
        return customers.register(sessionId(context), nombres, celular, tipoDocumento, numeroDocumento,
                operationId(context));
    }

    @Tool(name = "actualizar_datos_cliente", description = "Completa unicamente datos faltantes del cliente ya relacionado. Nunca reemplaza nombres, celular o documento validos por otros diferentes.")
    public CustomerResult actualizarDatosCliente(
            @ToolParam(description = "Nombres y apellidos; omitir si ya estan registrados", required = false) String nombres,
            @ToolParam(description = "Celular peruano; omitir si ya esta registrado", required = false) String celular,
            @ToolParam(description = "Tipo de documento: DNI, RUC o CE", required = false) TipoDocumento tipoDocumento,
            @ToolParam(description = "Numero de documento", required = false) String numeroDocumento,
            ToolContext context) {
        return customers.completeMissing(sessionId(context), nombres, celular, tipoDocumento, numeroDocumento,
                operationId(context));
    }

    @Tool(name = "relacionar_cliente_conversacion", description = "Relaciona un cliente exacto devuelto por buscar_cliente con la sesion y conversacion actuales, sin permitir clientes de otra empresa.")
    public CustomerResult relacionarCliente(
            @ToolParam(description = "Identificador exacto devuelto por buscar_cliente") Integer clienteId,
            ToolContext context) {
        return customers.link(sessionId(context), clienteId, operationId(context));
    }

    private Long sessionId(ToolContext context) {
        Object raw = values(context).get(AgentCatalogTools.SESSION_ID_CONTEXT_KEY);
        if (raw instanceof Number number) return number.longValue();
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                // Return the stable context error below.
            }
        }
        throw new IllegalStateException("Falta el contexto seguro de la sesion del agente");
    }

    private String operationId(ToolContext context) {
        Object raw = values(context).get(AgentCartTools.OPERATION_ID_CONTEXT_KEY);
        if (raw == null || raw.toString().isBlank()) {
            throw new IllegalStateException("Falta el identificador seguro de la operacion del agente");
        }
        return raw.toString().trim();
    }

    private Map<String, Object> values(ToolContext context) {
        return context == null ? Map.of() : context.getContext();
    }
}

package com.sistemapos.sistematextil.services.agent.fulfillment;

import java.util.Map;
import java.util.List;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.services.agent.cart.AgentCartTools;
import com.sistemapos.sistematextil.services.agent.ai.AgentShalomDataField;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogTools;
import com.sistemapos.sistematextil.services.agent.fulfillment.AgentFulfillmentService.FulfillmentResult;
import com.sistemapos.sistematextil.services.agent.fulfillment.AgentFulfillmentService.PickupBranchList;
import com.sistemapos.sistematextil.services.agent.fulfillment.AgentFulfillmentService.SavedProfileView;
import com.sistemapos.sistematextil.services.agent.fulfillment.ShalomAgencySearchService.AgencyView;
import com.sistemapos.sistematextil.services.agent.fulfillment.ShalomAgencySearchService.SearchResult;

@Component
public class AgentFulfillmentTools {
    private final AgentFulfillmentService fulfillment;

    public AgentFulfillmentTools(AgentFulfillmentService fulfillment) {
        this.fulfillment = fulfillment;
    }

    @Tool(name = "listar_sucursales_recojo", description = "Lista las sucursales activas de la empresa donde la clienta puede recoger su pedido.")
    public PickupBranchList listarSucursalesRecojo(ToolContext context) {
        return fulfillment.listPickupBranches(sessionId(context));
    }

    @Tool(name = "seleccionar_recojo_tienda", description = "Selecciona recojo en tienda despues de un comprobante aprobado. Guarda la sucursal, nombre y celular; si nombre o celular se omiten usa los datos validados del cliente.")
    public FulfillmentResult seleccionarRecojoTienda(
            @ToolParam(description = "Identificador exacto devuelto por listar_sucursales_recojo") Integer sucursalId,
            @ToolParam(description = "Nombre de quien recogera; omitir para usar el cliente registrado", required = false) String nombre,
            @ToolParam(description = "Celular de quien recogera; omitir para usar el cliente registrado", required = false) String celular,
            ToolContext context) {
        return fulfillment.selectPickup(sessionId(context), sucursalId, nombre, celular, operationId(context));
    }

    @Tool(name = "seleccionar_envio_shalom", description = "Selecciona envio por Shalom despues de un comprobante aprobado. Devuelve tambien datos Shalom guardados del cliente, si existen, para ofrecer reutilizarlos.")
    public FulfillmentResult seleccionarEnvioShalom(ToolContext context) {
        return fulfillment.selectShalom(sessionId(context), operationId(context));
    }

    @Tool(name = "buscar_agencias_shalom", description = "Busca agencias Shalom reales y activas por nombre, ciudad, provincia, departamento, abreviatura, direccion o referencia escrita libremente.")
    public SearchResult buscarAgenciasShalom(
            @ToolParam(description = "Texto libre con la agencia, ciudad o referencia indicada por la clienta") String consulta) {
        return fulfillment.searchAgencies(consulta);
    }

    @Tool(name = "obtener_agencia_shalom", description = "Obtiene una agencia Shalom exacta previamente encontrada.")
    public AgencyView obtenerAgenciaShalom(
            @ToolParam(description = "Identificador interno exacto devuelto por buscar_agencias_shalom") Long agenciaId) {
        return fulfillment.getAgency(agenciaId);
    }

    @Tool(name = "consultar_datos_shalom_guardados", description = "Consulta los ultimos datos Shalom confirmados del cliente de esta sesion.")
    public SavedProfileView consultarDatosShalomGuardados(ToolContext context) {
        return fulfillment.savedProfile(sessionId(context));
    }

    @Tool(name = "guardar_datos_shalom", description = "Valida nombres y apellidos, DNI, celular y busca la agencia indicada. Devuelve hasta tres agencias reales para que la clienta confirme una.")
    public FulfillmentResult guardarDatosShalom(
            @ToolParam(description = "Nombres y apellidos completos del destinatario") String nombresApellidos,
            @ToolParam(description = "DNI peruano de 8 digitos") String dni,
            @ToolParam(description = "Celular peruano valido") String celular,
            @ToolParam(description = "Nombre, ciudad, direccion o referencia de la agencia Shalom") String agencia,
            ToolContext context) {
        return fulfillment.saveShalomData(sessionId(context), nombresApellidos, dni, celular,
                agencia, operationId(context));
    }

    @Tool(name = "editar_datos_shalom", description = "Cambia solo los campos Shalom solicitados y conserva los demas. Permite cambiar nombre, DNI, celular, agencia o todos los datos.")
    public FulfillmentResult editarDatosShalom(
            @ToolParam(description = "Campos solicitados: NAME, DNI, PHONE, AGENCY o ALL") List<AgentShalomDataField> campos,
            @ToolParam(description = "Nuevo nombre completo, si fue proporcionado", required = false) String nombresApellidos,
            @ToolParam(description = "Nuevo DNI, si fue proporcionado", required = false) String dni,
            @ToolParam(description = "Nuevo celular, si fue proporcionado", required = false) String celular,
            @ToolParam(description = "Nueva referencia de agencia, si fue proporcionada", required = false) String agencia,
            ToolContext context) {
        return fulfillment.editShalomData(sessionId(context),campos,nombresApellidos,dni,celular,agencia,
                operationId(context));
    }

    @Tool(name = "confirmar_agencia_shalom", description = "Confirma exclusivamente una agencia ofrecida previamente, guarda el perfil y deja la entrega lista para generar la Nota de Venta.")
    public FulfillmentResult confirmarAgenciaShalom(
            @ToolParam(description = "Identificador interno exacto de una agencia ofrecida") Long agenciaId,
            ToolContext context) {
        return fulfillment.confirmShalomAgency(sessionId(context), agenciaId, operationId(context));
    }

    @Tool(name = "reutilizar_datos_shalom", description = "Reutiliza los datos Shalom guardados solo despues de que la clienta los confirme expresamente.")
    public FulfillmentResult reutilizarDatosShalom(ToolContext context) {
        return fulfillment.reuseSavedProfile(sessionId(context), operationId(context));
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

# Esquema del agente de WhatsApp

## Alcance

`CrmWhatsappAgentSchemaMigration` crea o verifica este esquema en la base MySQL configurada, tanto local como remota. La propiedad `crm.whatsapp.agent-schema-enabled` permite desactivarlo. La migración no inserta, actualiza ni elimina productos, clientes, conversaciones, stock, promociones o ventas.

## Tablas

| Tabla | Responsabilidad | Protección principal |
|---|---|---|
| `crm_whatsapp_agent_session` | Estado, resumen y versión de la compra activa. | Una sesión activa por conversación y `session_key` única. |
| `crm_whatsapp_agent_cart` | Totales y ciclo de vida del carrito. | Un carrito activo por sesión y clave idempotente. |
| `crm_whatsapp_agent_cart_item` | Variantes y precios capturados por el backend. | Una línea agregada por variante dentro del carrito. |
| `crm_whatsapp_agent_cart_promotion` | Promociones realmente aplicadas. | Clave de aplicación única y orden por promoción. |
| `crm_whatsapp_agent_job` | Procesamiento persistente de mensajes. | Un job por mensaje ancla y clave idempotente. |
| `crm_whatsapp_agent_tool_call` | Auditoría de herramientas y sus resultados. | Una ejecución lógica por clave idempotente. |
| `crm_whatsapp_agent_outbox` | Entrega ordenada y reintentable hacia WhatsApp. | Clave idempotente y secuencia única por sesión. |
| `crm_whatsapp_fulfillment` | Recojo o Shalom, incluidos DNI, agencia y captura. | Un registro por sesión y vínculo con pago/venta. |
| `crm_whatsapp_support_case` | Excepciones que requieren revisión futura. | Caso idempotente y cola indexada por estado/prioridad. |

## Relaciones con pagos

`crm_whatsapp_payment_request` incorpora:

- `id_agent_session`: sesión que originó la reserva.
- `agent_idempotency_key`: impide crear dos solicitudes para la misma acción lógica.

`crm_whatsapp_payment_evidence` incorpora:

- `id_agent_session`: sesión que está validando la imagen.
- `agent_validation_key`: impide ejecutar dos veces la misma validación lógica.

Ambas relaciones usan `ON DELETE SET NULL` para conservar el historial de pagos si una sesión deja de existir.

## Concurrencia e idempotencia

- La sesión y el carrito tienen `lock_version` para bloqueo optimista en JPA.
- `active_slot` es `1` solamente mientras el registro está activo; al cerrarlo pasa a `NULL`. La restricción única permite conservar el historial y evita dos sesiones o carritos activos simultáneos.
- Jobs y outbox tienen índices por estado, disponibilidad y lease para que varios workers no procesen el mismo trabajo.
- Las claves de idempotencia son generadas por el backend; Gemini nunca las inventa.
- Las referencias a producto, promoción, mensaje, pago y venta se protegen con claves foráneas.

## Eliminación y conservación

Los datos operativos de sesión, carrito, jobs, herramientas y outbox se eliminan en cascada si se elimina la conversación. Entrega y casos de soporte usan relaciones anulables para poder conservar información relacionada con una venta o pago ya registrado.

# Paso 10 - Herramientas del cliente

El agente dispone de cuatro herramientas:

- `buscar_cliente`
- `registrar_cliente`
- `actualizar_datos_cliente`
- `relacionar_cliente_conversacion`

## Identidad y alcance

Las búsquedas son exactas por celular o documento y siempre quedan limitadas a la empresa configurada en la
conexión WhatsApp. El celular peruano se guarda en su forma local de nueve dígitos, pero la búsqueda contempla
tanto esa forma como la variante con prefijo `51`. El documento se normaliza y valida como DNI, RUC o CE.

Gemini no recibe ni decide empresa, sucursal, conversación o sesión: esos datos llegan mediante el `ToolContext`
seguro del backend. Si celular y documento apuntan a clientes diferentes, la operación se rechaza y nunca fusiona
registros automáticamente.

## Escrituras seguras

Registrar, completar o relacionar un cliente bloquea en una sola transacción:

1. La sesión activa del agente.
2. La empresa de la conexión, para serializar altas y evitar duplicados concurrentes.
3. La conversación correspondiente.

Si el cliente ya existe, se reutiliza. `actualizar_datos_cliente` solo completa nombres provisionales, celular o
documento ausentes; no reemplaza datos válidos por una identidad distinta. La creación queda auditada con el
usuario técnico `ia-kiments@system.local` y, si este no está disponible, con un usuario activo de la sucursal.

Cada mutación utiliza `agentOperationId`, oculto al modelo, y persiste su resultado en
`crm_whatsapp_agent_tool_call`. Repetir una llamada devuelve el mismo resultado sin crear clientes ni transiciones
adicionales.

## Condición antes del pago

La sesión y la conversación quedan relacionadas con el mismo cliente. Solo se considera válido para pago un
cliente activo, no eliminado, con nombres reales y celular peruano válido. Al cumplirlo, el backend aplica
`DATOS_CLIENTE_COMPLETADOS` y avanza de `ESPERANDO_DATOS_CLIENTE` a `ESPERANDO_METODO_PAGO`. Si falta algún
dato, devuelve `missingFields` y conserva el estado actual.

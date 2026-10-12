# Paso 14 - Generacion de Nota de Venta

## Flujo

La Nota de Venta se genera automaticamente cuando la sesion llega a
`LISTO_PARA_VENTA`. Tambien se expone la herramienta segura
`generar_nota_venta` para el orquestador.

Antes de crearla, el backend bloquea y valida:

- La sesion activa y su estado.
- El comprobante aprobado inicialmente por Vision.
- La solicitud de pago y su reserva vigente.
- La modalidad de entrega completa.
- El cliente, la sucursal y el metodo de pago relacionados.
- Una serie activa y habilitada exclusivamente para `NOTA DE VENTA`.

## Reutilizacion del POS

La operacion llama `VentaService.registrarVentaReservadaDesdeCrm`. Este servicio:

- Bloquea la configuracion del comprobante y asigna el correlativo.
- Recalcula detalles, descuentos y total desde los datos persistidos.
- Registra el pago usando monto, metodo, fecha y operacion validados.
- Marca la venta con origen `CRM_WHATSAPP`.
- No vuelve a descontar stock porque las unidades ya estan reservadas.

Luego se consume la reserva y la misma venta se relaciona con
`crm_whatsapp_payment_request` y `crm_whatsapp_fulfillment`. Ambas relaciones
conservan la conversacion y la sesion que originaron la compra.

La sesion avanza a `VENTA_PENDIENTE_REVISION`. La aprobacion o rechazo humano
final se realiza en la fase siguiente.

## Idempotencia

La ejecucion usa el registro de herramientas idempotentes y, adicionalmente,
el bloqueo pesimista de la sesion y la solicitud de pago. Si el job o Gemini
repite la accion, se devuelve la Nota de Venta existente sin crear otra, sin
consumir nuevamente la reserva y sin incrementar otra vez el correlativo.

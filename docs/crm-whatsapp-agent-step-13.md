# Paso 13 - Modalidad de entrega

## Recojo en tienda

La herramienta `listar_sucursales_recojo` devuelve solamente sucursales activas de la misma empresa de la conexion
WhatsApp. `seleccionar_recojo_tienda` valida y guarda la sucursal, el nombre y el celular de quien recogera. Cuando
el cliente ya tiene esos datos validados pueden reutilizarse sin volver a pedirlos. El fulfillment queda `READY` y
la sesion avanza a `LISTO_PARA_VENTA`.

## Envio por Shalom

`seleccionar_envio_shalom` devuelve el enlace oficial `https://agencias.shalom.pe/` y deja la sesion en
`ESPERANDO_DATOS_SHALOM`. Antes de pedir una captura, `guardar_datos_shalom` exige y valida:

- nombres y apellidos;
- DNI peruano de 8 digitos;
- celular peruano valido;
- agencia o sede de Shalom;
- ciudad o destino.

Con los cinco datos completos el estado cambia a `ESPERANDO_CAPTURA_SHALOM`. La siguiente imagen entrante posterior
a ese cambio se asocia automaticamente al fulfillment, se conserva su ruta y mensaje original, y la sesion avanza a
`LISTO_PARA_VENTA`.

## Separacion de imagenes

El escaner de comprobantes solo considera medios cuando una sesion del agente esta en `ESPERANDO_COMPROBANTE` o
`VALIDANDO_COMPROBANTE`. El escaner de Shalom solo considera imagenes cuando la sesion esta en
`ESPERANDO_CAPTURA_SHALOM`, el fulfillment es `SHALOM` y los datos ya estan completos. Por ello una captura de sede
no puede enviarse a Gemini Vision como comprobante de pago.

Las operaciones de seleccion y datos son idempotentes, y la captura posee una restriccion unica por mensaje para
evitar asociaciones duplicadas.

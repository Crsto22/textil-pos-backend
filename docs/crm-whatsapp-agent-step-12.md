# Paso 12 - Analisis visual del comprobante

## Separacion de responsabilidades

Gemini Vision funciona exclusivamente como extractor OCR. Recibe el archivo original, pero no recibe el monto
esperado ni decide si el pago es valido. Devuelve una estructura con monto, moneda, fecha, hora, destinatario,
cuenta visible, numero de operacion, metodo de pago, texto visible y confianza.

Java vuelve a cargar y bloquear la sesion, la solicitud y la evidencia antes de tomar una decision. Comprueba:

- monto exacto contra el total persistido del carrito;
- fecha y hora dentro de la ventana de la solicitud;
- metodo, titular, alias o numero de la cuenta seleccionada;
- reserva de stock activa;
- duplicados por SHA-256, hash visual y numero de operacion dentro de la empresa;
- confianza minima configurable, con valor predeterminado de 85%.

## Resultados

- `APPROVED_INITIAL`: la evidencia queda `ACEPTABLE`, la reserva se conserva y la sesion avanza a
  `ESPERANDO_METODO_ENTREGA`.
- `OBSERVED`: faltan datos o la confianza es insuficiente. La sesion vuelve a `ESPERANDO_COMPROBANTE` mientras
  la reserva original siga vigente.
- `REJECTED`: existe una contradiccion verificable, por ejemplo monto, fecha, metodo, cuenta o duplicado.
  Puede enviarse otro comprobante mientras quede tiempo en la reserva.

La recepcion del archivo antes de los cuatro minutos detiene el vencimiento mientras Gemini lo procesa. El plazo
original se conserva para decidir si puede solicitarse una nueva captura. Una aprobacion inicial extiende la reserva
24 horas para completar entrega y generar la nota de venta en los siguientes pasos.

## Recuperacion e idempotencia

Las evidencias del agente nacen en `QUEUED`. El worker reclama una evidencia con bloqueos transaccionales, llama a
Gemini fuera de la transaccion y finaliza en otra transaccion. Los errores transitorios se reintentan hasta tres
veces. Un procesamiento interrumpido se recupera automaticamente despues de dos minutos y las claves de validacion,
hashes y bloqueos evitan procesamientos duplicados.

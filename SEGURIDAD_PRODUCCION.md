# Seguridad de produccion

## Variables obligatorias

- `JWT_SECRET`: secreto aleatorio de al menos 32 bytes; no reutilizar secretos de desarrollo.
- `CORS_ALLOWED_ORIGINS`: lista exacta de origenes HTTPS separados por coma. No usar `*`.
- `COOKIE_SECURE=true` cuando se use HTTPS.
- `COOKIE_SAME_SITE=None` exige `COOKIE_SECURE=true`.
- `COOKIE_DOMAIN`: dejarlo vacio con el BFF de Next.js. El BFF recibe el refresh token de Spring y crea una cookie host-only en el dominio donde se ejecuta el frontend.

## ADMS ZKTeco

- `ASISTENCIA_ADMS_MAX_EVENTS=1000`: marcaciones maximas por solicitud.
- `ASISTENCIA_ADMS_MAX_PAST_DAYS=7`: antiguedad maxima aceptada.
- `ASISTENCIA_ADMS_MAX_FUTURE_MINUTES=10`: tolerancia maxima para relojes adelantados.
- `ASISTENCIA_ADMS_MAX_BODY_BYTES=262144`: tamano maximo del cuerpo ADMS, medido en bytes.
- `ASISTENCIA_DUPLICADO_SEGUNDOS=120`: ventana para ignorar dobles huellas en el calculo.

Las rutas `/iclock/*` no usan JWT por compatibilidad con ADMS. El backend valida serial activo, trabajador activo, formato, tamano, lote y fecha; los rechazos se registran sin guardar el contenido de la solicitud.

El serial admite hasta 80 caracteres alfanumericos y `._:-`. Los campos opcionales de `ATTLOG` admiten hasta 20 caracteres para coincidir con las columnas de la base de datos.

Las correcciones manuales requieren JWT de `ADMINISTRADOR` o `SISTEMA`, un motivo de 10 a 255 caracteres y una fecha dentro de los ultimos 31 dias. Las marcaciones anuladas se conservan con usuario, fecha y motivo para auditoria.

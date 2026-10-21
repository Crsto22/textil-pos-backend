# KIMETS WhatsApp Bridge

Microservicio Node.js para conectar dos numeros de WhatsApp con el CRM usando Baileys por WebSocket.
Un coordinador administra sesiones aisladas para `SALES` y `SHIPPING` dentro del mismo contenedor.

## Variables

```env
WHATSAPP_BRIDGE_PORT=8095
WHATSAPP_BRIDGE_TOKEN=change-me
BACKEND_BASE_URL=http://backend:8080
BACKEND_WEBHOOK_PATH=/api/crm/whatsapp/webhook
WHATSAPP_SESSION_DIR=/app/storage/session
WHATSAPP_MEDIA_DIR=/app/storage/media
WHATSAPP_LOG_LEVEL=silent
```

## Endpoints

- `GET /health`
- `GET /connections` con `X-Bridge-Token`
- `GET /connections/{sales|shipping}/status` con `X-Bridge-Token`
- `GET /connections/{sales|shipping}/qr` con `X-Bridge-Token`
- `POST /connections/{sales|shipping}/logout` con `X-Bridge-Token`
- Los endpoints anteriores sin area se conservan temporalmente y apuntan a Ventas.

Las sesiones se guardan en `session/sales` y `session/shipping`. Al iniciar por primera vez,
las credenciales antiguas de la carpeta raiz se migran automaticamente a Ventas.

## Local

```bash
npm install
npm run dev
```

Luego consultar `GET http://localhost:8095/qr` con el token configurado y escanear el QR.

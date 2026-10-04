# KIMETS WhatsApp Bridge

Microservicio Node.js para conectar un solo numero de WhatsApp con el CRM usando Baileys por WebSocket.

## Variables

```env
WHATSAPP_BRIDGE_PORT=8095
WHATSAPP_BRIDGE_TOKEN=change-me
BACKEND_BASE_URL=http://backend:8080
BACKEND_WEBHOOK_PATH=/api/crm/whatsapp/webhook
WHATSAPP_SESSION_DIR=/app/storage/session
WHATSAPP_MEDIA_DIR=/app/storage/media
WHATSAPP_CLIENT_ID=kiments-main
WHATSAPP_LOG_LEVEL=silent
```

## Endpoints

- `GET /health`
- `GET /status` con `X-Bridge-Token`
- `GET /qr` con `X-Bridge-Token`
- `POST /send-text` con `X-Bridge-Token`
- `POST /logout` con `X-Bridge-Token`

## Local

```bash
npm install
npm run dev
```

Luego consultar `GET http://localhost:8095/qr` con el token configurado y escanear el QR.

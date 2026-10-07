import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import makeWASocket, {
  Browsers,
  DisconnectReason,
  downloadMediaMessage,
  fetchLatestBaileysVersion,
  useMultiFileAuthState,
  WAMessageStatus,
} from "@whiskeysockets/baileys";
import express from "express";
import multer from "multer";
import pino from "pino";
import qrcode from "qrcode";
import { prepareVoiceNote } from "./audio.js";
import { isAuthorized, isIndividualChatId, mediaDurationSeconds, mediaExtension, normalizeChatId, normalizePhone } from "./helpers.js";
import { prepareImageForWhatsApp } from "./image.js";
import { SendDeduplicator } from "./send-deduplicator.js";

const bridgeRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repoRoot = path.resolve(bridgeRoot, "..");

export const STATES = {
  INITIALIZING: "INITIALIZING",
  QR_REQUIRED: "QR_REQUIRED",
  CONNECTED: "CONNECTED",
  DISCONNECTED: "DISCONNECTED",
  AUTH_FAILURE: "AUTH_FAILURE",
};

const config = {
  port: Number(process.env.WHATSAPP_BRIDGE_PORT || 8095),
  token: process.env.WHATSAPP_BRIDGE_TOKEN || "valor_seguro",
  backendBaseUrl: (process.env.BACKEND_BASE_URL || "http://localhost:8080").replace(/\/$/, ""),
  backendWebhookPath: process.env.BACKEND_WEBHOOK_PATH || "/api/crm/whatsapp/webhook",
  sessionDir: process.env.WHATSAPP_SESSION_DIR || path.join(repoRoot, "storage/whatsapp/session"),
  mediaDir: process.env.WHATSAPP_MEDIA_DIR || path.join(repoRoot, "storage/whatsapp/media"),
  clientId: process.env.WHATSAPP_CLIENT_ID || "kiments-main",
};

const app = express();
app.use(express.json({ limit: "1mb" }));
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 50 * 1024 * 1024 } });

const logger = pino({ level: process.env.WHATSAPP_LOG_LEVEL || "silent" });
let sock = null;
let reconnecting = false;
let loggedOut = false;
let reconnectTimer;
let sessionReset;
let connectionGeneration = 0;
let credentialWrite = Promise.resolve();
const sendDeduplicator = new SendDeduplicator();
const lidToPhoneNumber = new Map();

const state = {
  status: STATES.INITIALIZING,
  qr: null,
  qrDataUrl: null,
  connectedNumber: null,
  lastActivityAt: null,
  lastError: null,
};

function requireBridgeToken(req, res, next) {
  if (isAuthorized(req.get("X-Bridge-Token"), config.token)) {
    return next();
  }

  return res.status(401).json({ message: "No autorizado" });
}

function publicStatus() {
  return {
    clientId: config.clientId,
    status: state.status,
    connectedNumber: state.connectedNumber,
    hasQr: Boolean(state.qr),
    lastActivityAt: state.lastActivityAt,
    lastError: state.lastError,
  };
}

async function ensureDirs() {
  await fs.mkdir(config.sessionDir, { recursive: true });
  await fs.mkdir(config.mediaDir, { recursive: true });
}

function getContent(message) {
  const content = message.message || {};
  return content.ephemeralMessage?.message || content.viewOnceMessage?.message || content.viewOnceMessageV2?.message || content;
}

function isViewOnceMessage(message) {
  const content = message?.message || {};
  return Boolean(content.viewOnceMessage || content.viewOnceMessageV2 || content.viewOnceMessageV2Extension);
}

function isStickerMessage(message) {
  return Boolean(getContent(message)?.stickerMessage);
}

function getMessageText(message) {
  const content = getContent(message);
  return (
    content.conversation ||
    content.extendedTextMessage?.text ||
    content.imageMessage?.caption ||
    content.videoMessage?.caption ||
    content.documentMessage?.caption ||
    ""
  );
}

function getQuotedMessageId(message) {
  const content = getContent(message);
  return (
    content.extendedTextMessage?.contextInfo?.stanzaId ||
    content.imageMessage?.contextInfo?.stanzaId ||
    content.videoMessage?.contextInfo?.stanzaId ||
    content.audioMessage?.contextInfo?.stanzaId ||
    content.documentMessage?.contextInfo?.stanzaId ||
    null
  );
}

function getRevokeMessageId(message) {
  const content = getContent(message);
  const protocol = content.protocolMessage;
  if (!protocol || protocol.type !== 0) {
    return null;
  }
  return protocol.key?.id || null;
}

function getBaileysMessageForQuote(message) {
  return {
    key: message.key,
    message: message.message,
    messageTimestamp: message.messageTimestamp,
    pushName: message.pushName,
  };
}

function parseQuotedMessage(value) {
  if (!value) {
    return undefined;
  }
  const quoted = typeof value === "string" ? JSON.parse(value) : value;
  if (!quoted?.key || !quoted?.message) {
    throw new Error("quoted invalido");
  }
  return quoted;
}

function parseMessageKey(value) {
  const key = typeof value === "string" ? JSON.parse(value) : value;
  if (!key?.remoteJid || !key?.id) {
    throw new Error("messageKey invalido");
  }
  return key;
}

function getMediaContent(content) {
  return (
    content.imageMessage ||
    content.videoMessage ||
    content.audioMessage ||
    content.documentMessage ||
    content.stickerMessage ||
    null
  );
}

function bridgeSendSignature(to, type, text) {
  return `${normalizeChatId(to)}|${type}|${String(text || "").trim()}`;
}

function registerPendingBridgeSend(signature) {
  return sendDeduplicator.register(signature);
}

function unregisterPendingBridgeSend(signature, token) {
  sendDeduplicator.unregister(signature, token);
}

function consumePendingBridgeSend(signature) {
  return sendDeduplicator.consumePending(signature);
}

function rememberSentFromBridge(messageId) {
  sendDeduplicator.rememberSent(messageId);
}

async function saveIncomingMedia(message) {
  const content = getContent(message);
  const media = getMediaContent(content);
  if (!media) {
    return null;
  }

  const buffer = await downloadMediaMessage(
    message,
    "buffer",
    {},
    { logger, reuploadRequest: sock?.updateMediaMessage },
  );
  if (!buffer?.length || !media.mimetype) {
    return null;
  }

  const fileName = `${message.key.id || Date.now()}.${mediaExtension(media.mimetype)}`;
  const diskPath = path.join(config.mediaDir, fileName);
  await fs.writeFile(diskPath, buffer);

  const durationSeconds = mediaDurationSeconds(media.seconds);
  return {
    mimeType: media.mimetype,
    fileName,
    storagePath: `/storage/whatsapp/media/${fileName}`,
    ...(durationSeconds ? { durationSeconds } : {}),
  };
}

async function postToBackend(payload) {
  const url = `${config.backendBaseUrl}${config.backendWebhookPath}`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Bridge-Token": config.token,
    },
    body: JSON.stringify({ clientId: config.clientId, ...payload }),
  });

  if (!response.ok) {
    const detail = await response.text().catch(() => "");
    throw new Error(`Backend webhook respondio ${response.status}${detail ? `: ${detail}` : ""}`);
  }
}

async function postConnectionState() {
  const url = `${config.backendBaseUrl}/api/crm/whatsapp/connection-event`;
  try {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Bridge-Token": config.token,
      },
      body: JSON.stringify(publicStatus()),
    });
    if (!response.ok) {
      const detail = await response.text().catch(() => "");
      console.warn(`Backend no acepto estado de conexion ${response.status}${detail ? `: ${detail}` : ""}`);
    }
  } catch (error) {
    console.warn("No se pudo sincronizar el estado de WhatsApp con el backend:", error.message);
  }
}

function mapMessageStatus(status) {
  if (status === WAMessageStatus.READ || status === WAMessageStatus.PLAYED) {
    return "read";
  }
  if (status === WAMessageStatus.DELIVERY_ACK) {
    return "delivered";
  }
  if (status === WAMessageStatus.SERVER_ACK) {
    return "sent";
  }
  if (status === WAMessageStatus.ERROR) {
    return "failed";
  }
  return null;
}

async function buildMediaPayload(file, caption) {
  const mimetype = file.mimetype || "application/octet-stream";
  const fileName = file.originalname || "archivo";
  const common = { mimetype, fileName, caption: caption || undefined };

  if (mimetype.startsWith("image/")) {
    const image = await prepareImageForWhatsApp(file.buffer, mimetype, fileName);
    return { image: image.buffer, caption: caption || undefined, mimetype: image.mimetype };
  }
  if (mimetype.startsWith("video/")) {
    return { video: file.buffer, caption: caption || undefined, mimetype };
  }
  if (mimetype.startsWith("audio/")) {
    return prepareVoiceNote(file.buffer);
  }
  return { document: file.buffer, ...common };
}

function getPhoneNumberFromMessage(message) {
  const candidate = [
    message.key.remoteJidAlt,
    message.key.participantAlt,
    message.key.remoteJid,
    message.key.participant,
  ].find((jid) => {
    const value = String(jid || "");
    return value.endsWith("@s.whatsapp.net") || value.endsWith("@c.us");
  });

  return candidate ? normalizePhone(candidate) : null;
}

function rememberLidMapping(mapping) {
  const lid = String(mapping?.lid || "");
  const pn = String(mapping?.pn || "");
  const phone = normalizePhone(pn);
  if (lid.endsWith("@lid") && phone) {
    lidToPhoneNumber.set(lid, phone);
  }
}

function getVisiblePhoneNumber(message) {
  return getPhoneNumberFromMessage(message) || lidToPhoneNumber.get(normalizeChatId(message.key.remoteJid)) || null;
}

function getUsernameFromMessage(message) {
  return message.key.remoteJidUsername || message.key.participantUsername || null;
}

async function handleIncomingMessage(message) {
  if (!message.message) {
    return;
  }

  const revokedMessageId = getRevokeMessageId(message);
  if (revokedMessageId) {
    await postToBackend({
      event: "message.deleted",
      messageId: revokedMessageId,
      timestamp: new Date().toISOString(),
      hasMedia: false,
    });
    return;
  }

  const from = normalizeChatId(message.key.remoteJid);
  if (!from || !isIndividualChatId(from)) {
    return;
  }

  const isOutgoing = Boolean(message.key.fromMe);
  if (isOutgoing && sendDeduplicator.consumeSent(message.key.id)) {
    return;
  }
  if (isOutgoing) {
    const content = getContent(message);
    const type = getMediaContent(content) ? "MEDIA" : "TEXT";
    const signature = bridgeSendSignature(from, type, getMessageText(message));
    if (consumePendingBridgeSend(signature)) {
      rememberSentFromBridge(message.key.id);
      return;
    }
  }

  const viewOnce = isViewOnceMessage(message);
  const sticker = isStickerMessage(message);
  const mediaPayload = viewOnce ? null : await saveIncomingMedia(message);
  const timestamp = Number(message.messageTimestamp || Math.floor(Date.now() / 1000));
  const payload = {
    event: isOutgoing ? "message.sent" : "message.received",
    messageId: message.key.id,
    from,
    phoneNumber: getVisiblePhoneNumber(message),
    username: getUsernameFromMessage(message),
    fromName: isOutgoing ? null : message.pushName || from,
    body: getMessageText(message),
    timestamp: new Date(timestamp * 1000).toISOString(),
    quotedMessageId: getQuotedMessageId(message),
    viewOnce,
    sticker,
    hasMedia: Boolean(mediaPayload),
    messageKey: message.key,
    baileysMessage: getBaileysMessageForQuote(message),
    ...(mediaPayload ? { media: mediaPayload } : {}),
  };

  state.lastActivityAt = new Date().toISOString();
  await postToBackend(payload);
}

async function handleMessageStatusUpdates(updates) {
  for (const item of updates) {
    const messageId = item.key?.id;
    if (messageId && item.update?.message === null) {
      await postToBackend({
        event: "message.deleted",
        messageId,
        timestamp: new Date().toISOString(),
        hasMedia: false,
      });
      continue;
    }
    const status = mapMessageStatus(item.update?.status);
    if (!messageId || !status) {
      continue;
    }

    await postToBackend({
      event: "message.status",
      messageId,
      status,
      timestamp: new Date().toISOString(),
      hasMedia: false,
    });
  }
}

async function resetSessionDir() {
  await fs.rm(config.sessionDir, { recursive: true, force: true });
  await fs.mkdir(config.sessionDir, { recursive: true });
}

function restartWithQr(unlink = false) {
  if (sessionReset) return sessionReset;
  sessionReset = (async () => {
    loggedOut = true;
    connectionGeneration++;
    clearTimeout(reconnectTimer);
    const previous = sock;
    const wasConnected = state.status === STATES.CONNECTED;
    sock = null;
    state.status = STATES.INITIALIZING;
    state.qr = null;
    state.qrDataUrl = null;
    state.connectedNumber = null;
    state.lastError = null;
    state.lastActivityAt = new Date().toISOString();
    void postConnectionState();
    if (previous) {
      previous.ev.removeAllListeners("creds.update");
      previous.ev.removeAllListeners("connection.update");
      try {
        if (unlink && wasConnected) await previous.logout();
      } catch (error) {
        console.warn("Sesion remota ya cerrada:", error.message);
      } finally {
        previous.end(undefined);
      }
    }
    await credentialWrite;
    await resetSessionDir();
    sendDeduplicator.clear();
    lidToPhoneNumber.clear();
    loggedOut = false;
    await connectToWhatsApp();
  })().catch((error) => {
    state.status = STATES.AUTH_FAILURE;
    state.lastError = error.message;
    void postConnectionState();
    throw error;
  }).finally(() => {
    loggedOut = false;
    sessionReset = null;
  });
  return sessionReset;
}

async function connectToWhatsApp() {
  const generation = ++connectionGeneration;
  reconnecting = false;
  state.status = STATES.INITIALIZING;
  state.lastActivityAt = new Date().toISOString();

  const { state: authState, saveCreds } = await useMultiFileAuthState(config.sessionDir);
  const { version } = await fetchLatestBaileysVersion();
  if (generation !== connectionGeneration) return;

  sock = makeWASocket({
    auth: authState,
    browser: Browsers.ubuntu(`KIMETS CRM ${config.clientId}`),
    logger,
    markOnlineOnConnect: false,
    printQRInTerminal: false,
    syncFullHistory: false,
    version,
  });

  const currentSocket = sock;
  sock.ev.on("creds.update", () => {
    if (generation !== connectionGeneration) return;
    credentialWrite = credentialWrite.then(saveCreds).catch((error) => {
      console.error("No se pudieron guardar credenciales:", error);
    });
  });
  sock.ev.on("lid-mapping.update", rememberLidMapping);

  sock.ev.on("connection.update", async (update) => {
    if (generation !== connectionGeneration || loggedOut) return;
    const { connection, lastDisconnect, qr } = update;

    if (qr) {
      const qrDataUrl = await qrcode.toDataURL(qr);
      if (generation !== connectionGeneration) return;
      state.status = STATES.QR_REQUIRED;
      state.qr = qr;
      state.qrDataUrl = qrDataUrl;
      state.connectedNumber = null;
      state.lastActivityAt = new Date().toISOString();
      state.lastError = null;
      console.log("WhatsApp QR generado");
      void postConnectionState();
    }

    if (connection === "open") {
      state.status = STATES.CONNECTED;
      state.qr = null;
      state.qrDataUrl = null;
      state.connectedNumber = currentSocket.user?.id || null;
      state.lastActivityAt = new Date().toISOString();
      state.lastError = null;
      console.log(`WhatsApp conectado${state.connectedNumber ? `: ${state.connectedNumber}` : ""}`);
      void postConnectionState();
    }

    if (connection === "close") {
      const statusCode = lastDisconnect?.error?.output?.statusCode || null;
      if (statusCode === DisconnectReason.loggedOut) {
        restartWithQr().catch((error) => console.error("No se pudo generar nuevo QR:", error));
        return;
      }
      const shouldReconnect = !loggedOut && statusCode !== DisconnectReason.loggedOut;
      state.status = shouldReconnect ? STATES.DISCONNECTED : STATES.AUTH_FAILURE;
      state.connectedNumber = null;
      state.lastActivityAt = new Date().toISOString();
      state.lastError = lastDisconnect?.error?.message || "Conexion cerrada";
      console.warn("WhatsApp desconectado:", state.lastError);
      void postConnectionState();

      if (shouldReconnect && !reconnecting) {
        reconnecting = true;
        reconnectTimer = setTimeout(() => connectToWhatsApp().catch((error) => {
          state.lastError = error.message;
          console.error("No se pudo reconectar WhatsApp:", error);
        }), 3000);
      }
    }
  });

  sock.ev.on("messages.upsert", async ({ messages, type }) => {
    const shouldProcess = type === "notify" || messages.some((message) => message.key?.fromMe);
    if (!shouldProcess) {
      return;
    }

    for (const message of messages) {
      if (type !== "notify" && !message.key?.fromMe) {
        continue;
      }
      try {
        await handleIncomingMessage(message);
      } catch (error) {
        state.lastError = error.message;
        console.error("No se pudo procesar mensaje entrante:", error);
      }
    }
  });

  sock.ev.on("messages.update", async (updates) => {
    try {
      await handleMessageStatusUpdates(updates);
    } catch (error) {
      state.lastError = error.message;
      console.error("No se pudo procesar estado de mensaje:", error);
    }
  });
}

await ensureDirs();

app.get("/health", (_req, res) => {
  res.json({ ok: true, ...publicStatus() });
});

app.get("/status", requireBridgeToken, (_req, res) => {
  res.json(publicStatus());
});

app.get("/qr", requireBridgeToken, (_req, res) => {
  if (!state.qr) {
    return res.status(404).json({ message: "QR no disponible", ...publicStatus() });
  }

  return res.json({
    qr: state.qr,
    qrDataUrl: state.qrDataUrl,
    ...publicStatus(),
  });
});

app.post("/send-text", requireBridgeToken, async (req, res) => {
  const to = normalizeChatId(req.body?.to);
  const message = String(req.body?.message || "").trim();

  if (!to || !message) {
    return res.status(400).json({ message: "to y message son requeridos" });
  }

  if (!isIndividualChatId(to)) {
    return res.status(400).json({ message: "to debe ser un chat individual valido" });
  }

  if (state.status !== STATES.CONNECTED || !sock) {
    return res.status(409).json({ message: "WhatsApp no esta conectado", ...publicStatus() });
  }

  const signature = bridgeSendSignature(to, "TEXT", message);
  const pendingToken = registerPendingBridgeSend(signature);
  try {
    const quoted = parseQuotedMessage(req.body?.quoted);
    const sent = await sock.sendMessage(to, { text: message }, quoted ? { quoted } : undefined);
    if (sent?.key?.id) {
      rememberSentFromBridge(sent.key.id);
    }
    state.lastActivityAt = new Date().toISOString();
    return res.json({
      messageId: sent?.key?.id,
      status: "sent",
      messageKey: sent?.key,
      baileysMessage: sent ? getBaileysMessageForQuote(sent) : null,
    });
  } catch (error) {
    state.lastError = error.message;
    return res.status(500).json({ message: "No se pudo enviar el mensaje", detail: error.message });
  } finally {
    unregisterPendingBridgeSend(signature, pendingToken);
  }
});

app.post("/send-media", requireBridgeToken, upload.single("file"), async (req, res) => {
  const to = normalizeChatId(req.body?.to);
  const caption = String(req.body?.caption || "").trim();

  if (!to || !req.file) {
    return res.status(400).json({ message: "to y file son requeridos" });
  }

  if (!isIndividualChatId(to)) {
    return res.status(400).json({ message: "to debe ser un chat individual valido" });
  }

  if (state.status !== STATES.CONNECTED || !sock) {
    return res.status(409).json({ message: "WhatsApp no esta conectado", ...publicStatus() });
  }

  const signature = bridgeSendSignature(to, "MEDIA", caption);
  const pendingToken = registerPendingBridgeSend(signature);
  try {
    const payload = await buildMediaPayload(req.file, caption);
    const quoted = parseQuotedMessage(req.body?.quoted);
    const sent = await sock.sendMessage(to, payload, quoted ? { quoted } : undefined);
    if (!sent?.key?.id) {
      throw new Error("WhatsApp no devolvio un identificador para el archivo enviado");
    }
    rememberSentFromBridge(sent.key.id);
    state.lastActivityAt = new Date().toISOString();
    return res.json({
      messageId: sent?.key?.id,
      status: "sent",
      messageKey: sent?.key,
      baileysMessage: sent ? getBaileysMessageForQuote(sent) : null,
    });
  } catch (error) {
    state.lastError = error.message;
    return res.status(500).json({ message: "No se pudo enviar el archivo", detail: error.message });
  } finally {
    unregisterPendingBridgeSend(signature, pendingToken);
  }
});

app.post("/delete-message", requireBridgeToken, async (req, res) => {
  if (state.status !== STATES.CONNECTED || !sock) {
    return res.status(409).json({ message: "WhatsApp no esta conectado", ...publicStatus() });
  }

  try {
    const key = parseMessageKey(req.body?.messageKey);
    if (!isIndividualChatId(normalizeChatId(key.remoteJid))) {
      return res.status(400).json({ message: "messageKey debe ser de un chat individual valido" });
    }
    const sent = await sock.sendMessage(key.remoteJid, { delete: key });
    state.lastActivityAt = new Date().toISOString();
    return res.json({ messageId: sent?.key?.id, status: "accepted" });
  } catch (error) {
    state.lastError = error.message;
    return res.status(500).json({ message: "No se pudo eliminar el mensaje", detail: error.message });
  }
});

app.post("/logout", requireBridgeToken, async (_req, res) => {
  try {
    await restartWithQr(true);
    return res.json({ message: "Sesion cerrada" });
  } catch (error) {
    state.lastError = error.message;
    loggedOut = false;
    return res.status(500).json({ message: "No se pudo cerrar la sesion", detail: error.message });
  }
});

connectToWhatsApp().catch((error) => {
  state.status = STATES.DISCONNECTED;
  state.lastError = error.message;
  void postConnectionState();
  console.error("No se pudo iniciar WhatsApp:", error);
});

app.listen(config.port, () => {
  console.log(`WhatsApp bridge escuchando en puerto ${config.port}`);
});

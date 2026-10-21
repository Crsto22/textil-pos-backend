import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";
import express from "express";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const port = Number(process.env.WHATSAPP_BRIDGE_PORT || 8095);
const token = process.env.WHATSAPP_BRIDGE_TOKEN || "valor_seguro";
const sessionRoot = process.env.WHATSAPP_SESSION_DIR || path.resolve(root, "../storage/whatsapp/session");
const mediaRoot = process.env.WHATSAPP_MEDIA_DIR || path.resolve(root, "../storage/whatsapp/media");

const areas = {
  sales: { area: "SALES", clientId: "kiments-sales", port: port + 1 },
  shipping: { area: "SHIPPING", clientId: "kiments-shipping", port: port + 2 },
};

const workers = new Map();

async function migrateLegacyDirectory(baseDir) {
  await fs.mkdir(baseDir, { recursive: true });
  const salesDir = path.join(baseDir, "sales");
  const shippingDir = path.join(baseDir, "shipping");
  await fs.mkdir(salesDir, { recursive: true });
  await fs.mkdir(shippingDir, { recursive: true });
  for (const entry of await fs.readdir(baseDir, { withFileTypes: true })) {
    if (entry.name === "sales" || entry.name === "shipping") continue;
    const source = path.join(baseDir, entry.name);
    const target = path.join(salesDir, entry.name);
    try {
      await fs.rename(source, target);
    } catch (error) {
      if (error?.code !== "EEXIST") throw error;
    }
  }
}

function startWorker(key) {
  const definition = areas[key];
  const child = spawn(process.execPath, [path.join(root, "src/server.js")], {
    env: {
      ...process.env,
      WHATSAPP_BRIDGE_PORT: String(definition.port),
      WHATSAPP_CLIENT_ID: definition.clientId,
      WHATSAPP_AREA: definition.area,
      WHATSAPP_SESSION_DIR: path.join(sessionRoot, key),
      WHATSAPP_MEDIA_DIR: path.join(mediaRoot, key),
    },
    stdio: "inherit",
  });
  workers.set(key, child);
  child.once("exit", () => {
    workers.delete(key);
    setTimeout(() => startWorker(key), 1500).unref();
  });
}

function authorized(req) {
  return req.get("X-Bridge-Token") === token;
}

function normalizeArea(value) {
  const key = String(value || "").trim().toLowerCase();
  return Object.hasOwn(areas, key) ? key : null;
}

async function workerJson(area, endpoint, method = "GET") {
  const response = await fetch(`http://127.0.0.1:${areas[area].port}${endpoint}`, {
    method,
    headers: { "X-Bridge-Token": token },
  });
  return { status: response.status, body: await response.json() };
}

function proxy(area, req, res, targetPath) {
  const headers = { ...req.headers, host: `127.0.0.1:${areas[area].port}` };
  const upstream = http.request({
    hostname: "127.0.0.1",
    port: areas[area].port,
    path: targetPath,
    method: req.method,
    headers,
  }, (response) => {
    res.status(response.statusCode || 502);
    for (const [name, value] of Object.entries(response.headers)) {
      if (value !== undefined) res.setHeader(name, value);
    }
    response.pipe(res);
  });
  upstream.on("error", () => res.status(503).json({ message: "Sesion de WhatsApp no disponible" }));
  req.pipe(upstream);
}

const app = express();

app.get("/health", async (_req, res) => {
  const statuses = await Promise.all(Object.keys(areas).map(async (area) => {
    try { return (await workerJson(area, "/health")).body; }
    catch { return { area: areas[area].area, status: "DISCONNECTED" }; }
  }));
  res.json({ status: "ok", connections: statuses });
});

app.get("/connections", async (req, res) => {
  if (!authorized(req)) return res.status(401).json({ message: "No autorizado" });
  const connections = await Promise.all(Object.keys(areas).map(async (area) => {
    try { return (await workerJson(area, "/status")).body; }
    catch { return { clientId: areas[area].clientId, area: areas[area].area, status: "DISCONNECTED", hasQr: false }; }
  }));
  res.json(connections);
});

app.use("/connections/:area", (req, res) => {
  if (!authorized(req)) return res.status(401).json({ message: "No autorizado" });
  const area = normalizeArea(req.params.area);
  if (!area) return res.status(400).json({ message: "Area de WhatsApp invalida" });
  const suffix = req.originalUrl.slice(`/connections/${req.params.area}`.length) || "/status";
  proxy(area, req, res, suffix);
});

// Compatibilidad temporal: los endpoints anteriores siguen apuntando a Ventas.
app.use((req, res) => {
  if (!authorized(req)) return res.status(401).json({ message: "No autorizado" });
  proxy("sales", req, res, req.originalUrl);
});

async function rejectDuplicateNumber() {
  try {
    const sales = (await workerJson("sales", "/status")).body;
    const shipping = (await workerJson("shipping", "/status")).body;
    if (sales.status === "CONNECTED" && shipping.status === "CONNECTED"
        && sales.connectedNumber && sales.connectedNumber === shipping.connectedNumber) {
      await workerJson("shipping", "/logout", "POST");
      console.error("El numero de Envios fue desconectado porque ya esta vinculado a Ventas");
    }
  } catch {
    // Los workers pueden estar iniciando; el siguiente ciclo volvera a validar.
  }
}

await migrateLegacyDirectory(sessionRoot);
await fs.mkdir(path.join(mediaRoot, "sales"), { recursive: true });
await fs.mkdir(path.join(mediaRoot, "shipping"), { recursive: true });
startWorker("sales");
startWorker("shipping");
setInterval(rejectDuplicateNumber, 2500).unref();

app.listen(port, () => {
  console.log(`WhatsApp bridge multi-sesion escuchando en ${port}`);
});

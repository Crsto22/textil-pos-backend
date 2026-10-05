import crypto from "node:crypto";
import mime from "mime-types";

export function normalizePhone(raw) {
  return String(raw || "").replace(/\D/g, "");
}

export function normalizeChatId(raw) {
  const value = String(raw || "").trim();
  if (value.endsWith("@lid") || value.endsWith("@s.whatsapp.net")) {
    return value;
  }
  if (value.endsWith("@c.us")) {
    return `${normalizePhone(value)}@s.whatsapp.net`;
  }

  const phone = normalizePhone(value);
  return phone ? `${phone}@s.whatsapp.net` : "";
}

export function isIndividualChatId(raw) {
  const value = String(raw || "").trim();
  return (value.endsWith("@s.whatsapp.net") || value.endsWith("@lid") || value.endsWith("@c.us")) && !value.endsWith("@g.us");
}

export function isAuthorized(headerValue, expectedToken) {
  if (!expectedToken || !headerValue) {
    return false;
  }

  const actual = Buffer.from(String(headerValue));
  const expected = Buffer.from(String(expectedToken));
  return actual.length === expected.length && crypto.timingSafeEqual(actual, expected);
}

export function mediaExtension(mimeType) {
  return mime.extension(mimeType || "") || "bin";
}

export function mediaDurationSeconds(value) {
  const seconds = Number(value);
  return Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : null;
}

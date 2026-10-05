import assert from "node:assert/strict";
import test from "node:test";
import { isAuthorized, isIndividualChatId, mediaDurationSeconds, mediaExtension, normalizeChatId, normalizePhone } from "../src/helpers.js";

test("normalizePhone deja solo digitos", () => {
  assert.equal(normalizePhone("+51 999-888-777"), "51999888777");
});

test("isAuthorized compara el token esperado", () => {
  assert.equal(isAuthorized("abc123", "abc123"), true);
  assert.equal(isAuthorized("abc123", "otro"), false);
  assert.equal(isAuthorized("", "abc123"), false);
});

test("normalizeChatId acepta numeros y LID", () => {
  assert.equal(normalizeChatId("51999888777"), "51999888777@s.whatsapp.net");
  assert.equal(normalizeChatId("163569568059625@lid"), "163569568059625@lid");
  assert.equal(normalizeChatId("51999888777@c.us"), "51999888777@s.whatsapp.net");
  assert.equal(normalizeChatId(""), "");
});

test("isIndividualChatId ignora grupos", () => {
  assert.equal(isIndividualChatId("51999888777@s.whatsapp.net"), true);
  assert.equal(isIndividualChatId("163569568059625@lid"), true);
  assert.equal(isIndividualChatId("51999888777@g.us"), false);
});

test("mediaExtension tiene fallback seguro", () => {
  assert.equal(mediaExtension("image/jpeg"), "jpeg");
  assert.equal(mediaExtension(""), "bin");
});

test("mediaDurationSeconds normaliza la duracion recibida por Baileys", () => {
  assert.equal(mediaDurationSeconds(59.2), 60);
  assert.equal(mediaDurationSeconds("60"), 60);
  assert.equal(mediaDurationSeconds(0), null);
  assert.equal(mediaDurationSeconds(undefined), null);
});

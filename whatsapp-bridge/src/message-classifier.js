const NON_VISIBLE_KEYS = new Set([
  "messageContextInfo",
  "senderKeyDistributionMessage",
  "secretEncryptedMessage",
  "conversation",
]);

// Only payloads that WhatsApp presents as user-visible content should produce an
// unsupported card. Unknown protocol/encryption payloads must stay invisible.
const VISIBLE_UNSUPPORTED_KEYS = new Set([
  "contactMessage",
  "contactsArrayMessage",
  "liveLocationMessage",
  "pollCreationMessage",
  "pollCreationMessageV2",
  "pollCreationMessageV3",
  "pollUpdateMessage",
  "groupInviteMessage",
  "productMessage",
  "listMessage",
  "listResponseMessage",
  "buttonsMessage",
  "buttonsResponseMessage",
  "templateMessage",
  "templateButtonReplyMessage",
  "eventMessage",
]);

export function classifyMessageContent(content = {}) {
  if (content.protocolMessage || content.albumMessage) return { kind: "ignore" };

  const reaction = content.reactionMessage;
  if (reaction) {
    const targetMessageId = reaction.key?.id || null;
    return targetMessageId
      ? { kind: "reaction", targetMessageId, emoji: reaction.text || "" }
      : { kind: "ignore" };
  }

  const location = content.locationMessage;
  if (location && Number.isFinite(Number(location.degreesLatitude))
      && Number.isFinite(Number(location.degreesLongitude))) {
    return {
      kind: "message",
      contentType: "LOCATION",
      body: location.name || location.address || "Ubicación compartida",
      location: {
        latitude: Number(location.degreesLatitude),
        longitude: Number(location.degreesLongitude),
        name: location.name || null,
        address: location.address || null,
      },
    };
  }

  const text = content.conversation || content.extendedTextMessage?.text
    || content.imageMessage?.caption || content.videoMessage?.caption
    || content.documentMessage?.caption || "";
  const hasMedia = Boolean(content.imageMessage || content.videoMessage || content.audioMessage
    || content.documentMessage || content.stickerMessage);
  if (String(text).trim() || hasMedia) {
    return { kind: "message", contentType: null, body: String(text) };
  }

  const hasVisibleUnsupportedContent = Object.keys(content)
    .some((key) => VISIBLE_UNSUPPORTED_KEYS.has(key) && !NON_VISIBLE_KEYS.has(key));
  return hasVisibleUnsupportedContent
    ? { kind: "message", contentType: "UNSUPPORTED", body: "Contenido de WhatsApp no compatible" }
    : { kind: "ignore" };
}

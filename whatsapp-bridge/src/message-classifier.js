const NON_VISIBLE_KEYS = new Set(["messageContextInfo", "senderKeyDistributionMessage", "conversation"]);

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

  const potentiallyVisible = Object.keys(content).some((key) => !NON_VISIBLE_KEYS.has(key));
  return potentiallyVisible
    ? { kind: "message", contentType: "UNSUPPORTED", body: "Mensaje no compatible" }
    : { kind: "ignore" };
}

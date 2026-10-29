import test from "node:test";
import assert from "node:assert/strict";

import { classifyMessageContent } from "../src/message-classifier.js";

test("ignores protocol and album containers", () => {
  assert.equal(classifyMessageContent({ protocolMessage: { type: "EPHEMERAL_SETTING" } }).kind, "ignore");
  assert.equal(classifyMessageContent({ albumMessage: { expectedImageCount: 2 } }).kind, "ignore");
});

test("classifies reactions including removals", () => {
  assert.deepEqual(classifyMessageContent({ reactionMessage: { key: { id: "target-1" }, text: "👍" } }), {
    kind: "reaction", targetMessageId: "target-1", emoji: "👍",
  });
  assert.equal(classifyMessageContent({ reactionMessage: { key: { id: "target-1" }, text: "" } }).emoji, "");
});

test("classifies locations and unsupported visible payloads", () => {
  const location = classifyMessageContent({ locationMessage: {
    degreesLatitude: -12.0464, degreesLongitude: -77.0428, name: "Lima", address: "Centro",
  } });
  assert.equal(location.contentType, "LOCATION");
  assert.equal(location.location.latitude, -12.0464);
  assert.equal(classifyMessageContent({ secretEncryptedMessage: { encPayload: "x" } }).contentType, "UNSUPPORTED");
});

test("does not publish empty context-only messages", () => {
  assert.equal(classifyMessageContent({ messageContextInfo: {} }).kind, "ignore");
  assert.equal(classifyMessageContent({ conversation: "" }).kind, "ignore");
});

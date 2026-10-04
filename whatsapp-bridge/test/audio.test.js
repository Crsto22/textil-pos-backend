import test from "node:test";
import assert from "node:assert/strict";
import { prepareVoiceNote } from "../src/audio.js";

test("voice notes use real OGG/Opus and reject invalid recordings", async () => {
  const wav = Buffer.alloc(44 + 16000 * 2);
  wav.write("RIFF", 0);
  wav.writeUInt32LE(wav.length - 8, 4);
  wav.write("WAVEfmt ", 8);
  wav.writeUInt32LE(16, 16);
  wav.writeUInt16LE(1, 20);
  wav.writeUInt16LE(1, 22);
  wav.writeUInt32LE(16000, 24);
  wav.writeUInt32LE(32000, 28);
  wav.writeUInt16LE(2, 32);
  wav.writeUInt16LE(16, 34);
  wav.write("data", 36);
  wav.writeUInt32LE(wav.length - 44, 40);
  const result = await prepareVoiceNote(wav);
  assert.equal(result.audio.subarray(0, 4).toString(), "OggS");
  assert.ok(result.audio.includes(Buffer.from("OpusHead")));
  assert.equal(result.ptt, true);
  assert.ok(result.seconds >= 1);
  await assert.rejects(prepareVoiceNote(Buffer.from("invalid audio")));
});

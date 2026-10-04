import { execFile } from "node:child_process";
import { promisify } from "node:util";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import ffmpeg from "ffmpeg-static";
import { getAudioDuration } from "@whiskeysockets/baileys";

const execute = promisify(execFile);

export async function prepareVoiceNote(buffer) {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "kiments-audio-"));
  try {
    const input = path.join(dir, "input");
    const output = path.join(dir, "voice.ogg");
    await fs.writeFile(input, buffer);
    await execute(ffmpeg, ["-hide_banner", "-loglevel", "error", "-i", input,
      "-vn", "-ac", "1", "-ar", "48000", "-c:a", "libopus", "-b:a", "32k",
      "-application", "voip", "-f", "ogg", output],
    { timeout: 60000, windowsHide: true });
    const audio = await fs.readFile(output);
    const duration = await getAudioDuration(audio);
    if (!Number.isFinite(duration) || duration <= 0) {
      throw new Error("El audio no contiene una grabacion valida");
    }
    return { audio, mimetype: "audio/ogg; codecs=opus", ptt: true, seconds: Math.max(1, Math.ceil(duration)) };
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
}

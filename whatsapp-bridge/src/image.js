import { execFile } from "node:child_process";
import { promisify } from "node:util";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import ffmpeg from "ffmpeg-static";

const execute = promisify(execFile);

function isWebp(buffer, mimetype) {
  return mimetype.toLowerCase().split(";", 1)[0].trim() === "image/webp"
    || (buffer.length >= 12
      && buffer.subarray(0, 4).toString("ascii") === "RIFF"
      && buffer.subarray(8, 12).toString("ascii") === "WEBP");
}

export async function prepareImageForWhatsApp(buffer, mimetype, fileName) {
  if (!isWebp(buffer, mimetype)) {
    return { buffer, mimetype, fileName };
  }

  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "kiments-image-"));
  try {
    const input = path.join(dir, "input.webp");
    const output = path.join(dir, "image.png");
    await fs.writeFile(input, buffer);
    await execute(ffmpeg, [
      "-hide_banner", "-loglevel", "error", "-i", input,
      "-frames:v", "1", "-f", "image2", output,
    ], { timeout: 60000, windowsHide: true });

    const converted = await fs.readFile(output);
    const normalizedName = String(fileName || "imagen.webp").replace(/\.webp$/i, ".png");
    return { buffer: converted, mimetype: "image/png", fileName: normalizedName };
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
}

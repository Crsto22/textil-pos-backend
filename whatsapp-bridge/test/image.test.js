import { execFile } from "node:child_process";
import { promisify } from "node:util";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import ffmpeg from "ffmpeg-static";
import { prepareImageForWhatsApp } from "../src/image.js";

const execute = promisify(execFile);

test("WebP guides are converted to PNG before sending them to WhatsApp", async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "kiments-image-test-"));
  try {
    const source = path.join(dir, "source.ppm");
    const webp = path.join(dir, "source.webp");
    const ppm = Buffer.concat([Buffer.from("P6\n2 2\n255\n"), Buffer.from([
      255, 255, 255, 0, 0, 0,
      0, 0, 0, 255, 255, 255,
    ])]);
    await fs.writeFile(source, ppm);
    await execute(ffmpeg, ["-hide_banner", "-loglevel", "error", "-i", source, webp], {
      timeout: 60000,
      windowsHide: true,
    });

    const result = await prepareImageForWhatsApp(await fs.readFile(webp), "image/webp", "guia.webp");

    assert.equal(result.mimetype, "image/png");
    assert.equal(result.fileName, "guia.png");
    assert.equal(result.buffer.subarray(1, 4).toString("ascii"), "PNG");
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
});

test("JPEG images are sent without transcoding", async () => {
  const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xd9]);
  const result = await prepareImageForWhatsApp(jpeg, "image/jpeg", "foto.jpg");
  assert.equal(result.buffer, jpeg);
  assert.equal(result.mimetype, "image/jpeg");
  assert.equal(result.fileName, "foto.jpg");
});

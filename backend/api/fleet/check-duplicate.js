/**
 * POST /api/fleet/check-duplicate
 *
 * Body: { embedding: float[512] }
 *   Checks the given face embedding against every enrolled driver's stored
 *   embeddings.  Used during self-registration to prevent duplicate accounts.
 *
 *   → 200 { isDuplicate: false }
 *   → 200 { isDuplicate: true, name: "Driver Name", score: 0.91 }
 *   → 400 if embedding is missing / malformed
 *
 * Protected by: Authorization: Bearer <SYNC_SECRET>
 */
import { initFleetSchema, listEnrolledWithEmbeddings } from "../_postgres.js";
import { createDecipheriv } from "crypto";

function requireAuth(req, res) {
  const auth = req.headers["authorization"] || "";
  if (auth !== `Bearer ${process.env.SYNC_SECRET?.trim()}`) {
    res.status(401).json({ error: "Unauthorized" });
    return false;
  }
  return true;
}

function decryptEmbeddings(embeddingsEnc) {
  const keyHex = process.env.FLEET_EMBED_KEY;
  if (!keyHex) throw new Error("FLEET_EMBED_KEY not set");
  const key = Buffer.from(keyHex, "hex");
  const raw = Buffer.from(embeddingsEnc, "base64");
  const iv = raw.slice(0, 12);
  const tag = raw.slice(12, 28);
  const ciphertext = raw.slice(28);
  const decipher = createDecipheriv("aes-256-gcm", key, iv);
  decipher.setAuthTag(tag);
  const decrypted = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
  return JSON.parse(decrypted.toString("utf8")); // float[][]
}

function cosine(a, b) {
  let dot = 0, magA = 0, magB = 0;
  for (let i = 0; i < a.length; i++) {
    dot += a[i] * b[i];
    magA += a[i] * a[i];
    magB += b[i] * b[i];
  }
  return dot / (Math.sqrt(magA) * Math.sqrt(magB));
}

function bestScore(stored, query) {
  return Math.max(...stored.map((e) => cosine(e, query)));
}

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "POST only" });
  if (!requireAuth(req, res)) return;

  const { embedding } = req.body;
  if (!Array.isArray(embedding) || embedding.length !== 512) {
    return res.status(400).json({ error: "embedding must be a float[512] array" });
  }

  try {
    await initFleetSchema();
    const drivers = await listEnrolledWithEmbeddings();

    const threshold = parseFloat(process.env.FACE_THRESHOLD) || 0.75;

    for (const driver of drivers) {
      const stored = decryptEmbeddings(driver.embeddingsEnc);
      const score = bestScore(stored, embedding);
      if (score >= threshold) {
        return res.json({ isDuplicate: true, name: driver.name, score });
      }
    }

    return res.json({ isDuplicate: false });
  } catch (err) {
    console.error("check-duplicate error:", err);
    return res.status(500).json({ error: "Server error" });
  }
}

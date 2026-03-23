/**
 * POST /api/fleet/verify
 *
 * Body A — PIN check: { pin }
 *   Server computes HMAC-SHA256(pin, PIN_PEPPER) and looks up by hash.
 *   → 200 { exists: true, driverId, name }
 *   → 404 { error: "Driver not found" }
 *
 * Body B — Face verify: { driverId, embedding: float[512] }
 *   Looks up driver by ID, decrypts stored embeddings, runs cosine similarity.
 *   → 200 { matched: bool, score: float, driverId, name }
 *   → 404 if driver not found
 *   → 400 if driver not enrolled
 *
 * Protected by: Authorization: Bearer <SYNC_SECRET>
 */
import { initFleetSchema, lookupById } from "../_postgres.js";
import { createDecipheriv } from "crypto";
import { verify as argon2Verify } from "@node-rs/argon2";

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
  // Format: [12-byte IV][16-byte auth tag][ciphertext]
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

  const { pin, driverId, embedding } = req.body;

  try {
    await initFleetSchema();

    // ── Body A: PIN check — { driverId, pin } ───────────────────────────────
    // Driver is already identified (selected from picker); PIN confirms identity.
    if (driverId && pin && !embedding) {
      const driver = await lookupById(driverId);
      if (!driver) return res.status(404).json({ error: "Driver not found" });

      if (!driver.pinArgon2) {
        return res.status(400).json({ error: "Driver has no PIN set" });
      }

      // Argon2id verify — memory-hard (64 MB), per-driver salt in hash string
      const ok = await argon2Verify(driver.pinArgon2, String(pin));
      if (!ok) return res.status(401).json({ error: "Incorrect PIN" });

      return res.json({ exists: true, driverId: driver.id, name: driver.name });
    }

    // ── Body B: Face verify ─────────────────────────────────────────────────
    if (driverId && embedding) {
      const driver = await lookupById(driverId);
      if (!driver) return res.status(404).json({ error: "Driver not found" });
      if (!driver.isEnrolled || !driver.embeddingsEnc) {
        return res.status(400).json({ error: "Driver not enrolled" });
      }
      const stored = decryptEmbeddings(driver.embeddingsEnc);
      const score = bestScore(stored, embedding);
      const matched = score >= (parseFloat(process.env.FACE_THRESHOLD) || 0.75);
      return res.json({ matched, score, driverId: driver.id, name: driver.name });
    }

    return res.status(400).json({ error: "Provide { driverId, pin } for PIN check or { driverId, embedding } for face verify" });
  } catch (err) {
    console.error("verify.js error:", err);
    return res.status(500).json({ error: "Server error during verification" });
  }
}

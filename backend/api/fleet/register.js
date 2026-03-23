/**
 * POST /api/fleet/register
 *
 * Body: { id, name, pinHash, embeddings: float[][] }
 *   → 200 { ok: true }
 *   → 409 if pinHash already taken by a different driver
 *
 * Server encrypts embeddings with FLEET_EMBED_KEY before storing.
 * Protected by: Authorization: Bearer <SYNC_SECRET>
 */
import { initFleetSchema, upsertFleetDriver } from "../_postgres.js";
import { randomBytes, createCipheriv } from "crypto";
import { hash as argon2Hash } from "@node-rs/argon2";

function requireAuth(req, res) {
  const auth = req.headers["authorization"] || "";
  if (auth !== `Bearer ${process.env.SYNC_SECRET?.trim()}`) {
    res.status(401).json({ error: "Unauthorized" });
    return false;
  }
  return true;
}

function encryptEmbeddings(embeddings) {
  const keyHex = process.env.FLEET_EMBED_KEY;
  if (!keyHex) throw new Error("FLEET_EMBED_KEY not set");
  const key = Buffer.from(keyHex, "hex");
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const plaintext = Buffer.from(JSON.stringify(embeddings), "utf8");
  const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  const tag = cipher.getAuthTag();
  // Format: [12-byte IV][16-byte auth tag][ciphertext] → Base64
  return Buffer.concat([iv, tag, ciphertext]).toString("base64");
}

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "POST only" });
  if (!requireAuth(req, res)) return;

  const { id, name, pin, embeddings } = req.body;
  if (!id || !name || !pin) {
    return res.status(400).json({ error: "id, name, pin required" });
  }

  try {
    await initFleetSchema();

    // Argon2id: memory-hard (64 MB), per-driver random salt embedded in hash string
    // No global uniqueness needed — PIN is a per-driver credential, not a lookup key
    const pinArgon2 = await argon2Hash(String(pin), { memoryCost: 65536, timeCost: 3, parallelism: 1 });

    const isEnrolled = Array.isArray(embeddings) && embeddings.length > 0;
    const embeddingsEnc = isEnrolled ? encryptEmbeddings(embeddings) : null;

    await upsertFleetDriver({ id, name, pinArgon2, embeddingsEnc, isEnrolled });

    return res.json({ ok: true });
  } catch (err) {
    console.error("register.js error:", err);
    return res.status(500).json({ error: "Server error during registration" });
  }
}

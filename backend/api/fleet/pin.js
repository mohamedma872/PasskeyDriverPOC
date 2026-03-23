/**
 * GET /api/fleet/pin
 *
 * Returns a server-guaranteed unique 6-digit PIN.
 * Hashes each candidate with SHA-256 + pepper and checks against fleet_drivers
 * before returning — so the client never gets a PIN that's already taken.
 *
 * Protected by: Authorization: Bearer <SYNC_SECRET>
 */
import { initFleetSchema, lookupByPinHash } from "../_postgres.js";
import { createHash } from "crypto";

const PEPPER = "WH_FLEET_2026";

function hashPin(pin) {
  return createHash("sha256").update(pin + PEPPER, "utf8").digest("hex");
}

function randomPin() {
  return String(Math.floor(100000 + Math.random() * 900000));
}

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Authorization");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "GET") return res.status(405).json({ error: "GET only" });

  const auth = req.headers["authorization"] || "";
  if (auth !== `Bearer ${process.env.SYNC_SECRET?.trim()}`) {
    return res.status(401).json({ error: "Unauthorized" });
  }

  try {
    // Auto-init schema on first deploy (idempotent CREATE TABLE IF NOT EXISTS)
    await initFleetSchema();

    // 900k possible PINs — collision odds are tiny, but retry up to 10x to be safe
    for (let i = 0; i < 10; i++) {
      const pin = randomPin();
      const hash = hashPin(pin);
      const existing = await lookupByPinHash(hash);
      if (!existing) return res.json({ pin });
    }

    return res.status(500).json({ error: "Could not generate a unique PIN" });
  } catch (err) {
    console.error("pin.js error:", err);
    return res.status(500).json({ error: "Server error generating PIN" });
  }
}

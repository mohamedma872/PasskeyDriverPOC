/**
 * POST /api/fleet/login
 * Fallback login with username + password.
 * Body: { username, password }
 * → 200 { ok: true, driverId, name }
 * → 401 if credentials wrong
 */
import { initFleetSchema, lookupByUsername } from "../_postgres.js";
import { verify as argon2Verify } from "@node-rs/argon2";

function requireAuth(req, res) {
  const auth = req.headers["authorization"] || "";
  if (auth !== `Bearer ${process.env.SYNC_SECRET?.trim()}`) {
    res.status(401).json({ error: "Unauthorized" });
    return false;
  }
  return true;
}

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "POST only" });
  if (!requireAuth(req, res)) return;

  const { username, password } = req.body || {};
  if (!username || !password) {
    return res.status(400).json({ error: "username and password required" });
  }

  try {
    await initFleetSchema();
    const driver = await lookupByUsername(username.trim().toLowerCase());
    if (!driver || !driver.passwordArgon2) {
      return res.status(401).json({ error: "Invalid credentials" });
    }
    const match = await argon2Verify(driver.passwordArgon2, String(password));
    if (!match) {
      return res.status(401).json({ error: "Invalid credentials" });
    }
    return res.json({ ok: true, driverId: driver.id, name: driver.name });
  } catch (err) {
    console.error("login.js error:", err);
    return res.status(500).json({ error: "Server error" });
  }
}

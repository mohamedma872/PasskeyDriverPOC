/**
 * One-time schema setup endpoint.
 * Call once after first deploy: POST /api/setup?secret=YOUR_SETUP_SECRET
 * Creates PostgreSQL tables if they don't exist.
 */
import { initSchema } from "./_postgres.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ error: "POST only" });

  // Protect with a secret so only you can call it
  if (req.query.secret !== process.env.SETUP_SECRET) {
    return res.status(401).json({ error: "Unauthorized" });
  }

  await initSchema();
  return res.json({ ok: true, message: "Schema initialized" });
}

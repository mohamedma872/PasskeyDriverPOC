/**
 * One-time schema setup endpoint.
 * Call once after first deploy: POST /api/setup?secret=YOUR_SETUP_SECRET
 * Creates PostgreSQL tables if they don't exist.
 */
import { initSchema, initFleetSchema } from "./_postgres.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ error: "POST only" });

  if (req.query.secret !== process.env.SETUP_SECRET) {
    return res.status(401).json({ error: "Unauthorized" });
  }

  await initSchema();
  await initFleetSchema();
  return res.json({ ok: true, message: "Schema initialized" });
}

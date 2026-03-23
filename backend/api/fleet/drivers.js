/**
 * GET /api/fleet/drivers
 *
 * Returns the list of all registered fleet drivers (id, name, isEnrolled).
 * Used by the Android driver-picker screen so the user selects themselves
 * before entering their PIN.
 *
 * Protected by: Authorization: Bearer <SYNC_SECRET>
 */
import { initFleetSchema, listFleetDrivers } from "../_postgres.js";

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
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "GET") return res.status(405).json({ error: "GET only" });
  if (!requireAuth(req, res)) return;

  try {
    await initFleetSchema();
    const drivers = await listFleetDrivers();
    return res.json({ drivers });
  } catch (err) {
    console.error("drivers.js error:", err);
    return res.status(500).json({ error: "Server error" });
  }
}

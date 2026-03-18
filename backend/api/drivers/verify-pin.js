import { verifyDriverPin } from "../_postgres.js";

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });

  try {
    const { driverId, pin } = req.body;
    if (!driverId || !pin) {
      return res.status(400).json({ error: "driverId and pin are required" });
    }

    const valid = await verifyDriverPin(driverId, pin);
    return res.json({ valid });
  } catch (err) {
    console.error("POST /api/drivers/verify-pin error:", err.message);
    return res.status(500).json({ error: err.message });
  }
}

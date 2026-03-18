import { getDriverById } from "../_postgres.js";

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "GET") return res.status(405).json({ error: "Method not allowed" });

  try {
    const { id } = req.query;
    if (!id) return res.status(400).json({ error: "id is required" });

    const driver = await getDriverById(id);
    if (!driver) return res.status(404).json({ error: "Driver not found" });

    return res.json({ driver });
  } catch (err) {
    console.error("GET /api/drivers/[id] error:", err.message);
    return res.status(500).json({ error: err.message });
  }
}

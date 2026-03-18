import { getAllDrivers } from "../_postgres.js";

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "GET") return res.status(405).json({ error: "Method not allowed" });

  try {
    const drivers = await getAllDrivers();
    return res.json({ drivers });
  } catch (err) {
    console.error("GET /api/drivers error:", err.message);
    return res.status(500).json({ error: err.message });
  }
}

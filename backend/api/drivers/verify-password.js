import { verifyDriverPassword } from "../_postgres.js";

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });

  try {
    const { username, password } = req.body;
    if (!username || !password) {
      return res.status(400).json({ error: "username and password are required" });
    }

    const driver = await verifyDriverPassword(username, password);
    if (!driver) return res.json({ valid: false });

    return res.json({ valid: true, driverId: driver.id, name: driver.name, username: driver.username });
  } catch (err) {
    console.error("POST /api/drivers/verify-password error:", err.message);
    return res.status(500).json({ error: err.message });
  }
}

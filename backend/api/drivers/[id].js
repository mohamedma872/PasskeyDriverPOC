import { getDriverById } from "../_postgres.js";

export default async function handler(req, res) {
  if (req.method !== "GET") return res.status(405).json({ error: "Method not allowed" });
  const { id } = req.query;
  if (!id) return res.status(400).json({ error: "Missing driver id" });
  try {
    const driver = await getDriverById(id);
    if (!driver) return res.status(404).json({ error: "Driver not found" });
    res.status(200).json({ driver });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
}

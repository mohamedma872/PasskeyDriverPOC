import { markCardIssued } from "../_postgres.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });
  const { driverId } = req.body;
  if (!driverId) return res.status(400).json({ error: "Missing driverId" });
  try {
    await markCardIssued(driverId);
    res.status(200).json({ success: true });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
}

import { createDriver } from "../_postgres.js";
import { randomUUID } from "crypto";

function generateUsername(name) {
  const parts = name.trim().toLowerCase().split(/\s+/);
  if (parts.length >= 2) return `${parts[0]}.${parts[parts.length - 1]}`;
  return parts[0];
}

function randomAlphanumeric(length) {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no O/0, I/1 ambiguity
  let result = "";
  for (let i = 0; i < length; i++) {
    result += chars[Math.floor(Math.random() * chars.length)];
  }
  return result;
}

function randomPin() {
  return String(Math.floor(1000 + Math.random() * 9000)); // 4 digits, never starts with 0
}

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });

  try {
    const { name } = req.body;
    if (!name || !name.trim()) {
      return res.status(400).json({ error: "name is required" });
    }

    const driverId = "drv_" + randomUUID().replace(/-/g, "").slice(0, 8);
    const username = generateUsername(name);
    const password = randomAlphanumeric(6);
    const pin = randomPin();

    await createDriver(driverId, name.trim(), username, password, pin);

    return res.json({ id: driverId, name: name.trim(), username, password, pin });
  } catch (err) {
    console.error("POST /api/drivers/create error:", err.message);
    return res.status(500).json({ error: err.message });
  }
}

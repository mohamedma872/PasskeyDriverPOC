/**
 * POST /api/fleet/seed-demo?secret=SETUP_SECRET
 * Seeds 2 demo drivers with username + password for testing the fallback login.
 * Safe to call multiple times (upsert).
 *
 * Demo credentials (plain text):
 *   ahmed.khalil / Fleet@2024
 *   omar.hassan  / Driver@2024
 */
import { initFleetSchema, upsertFleetDriver } from "../_postgres.js";
import { hash as argon2Hash } from "@node-rs/argon2";

const DEMO_DRIVERS = [
  { id: "demo-driver-001", name: "Ahmed Khalil", username: "ahmed.khalil", password: "Fleet@2024"  },
  { id: "demo-driver-002", name: "Omar Hassan",  username: "omar.hassan",  password: "Driver@2024" },
];

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ error: "POST only" });
  if (req.query.secret !== process.env.SETUP_SECRET?.trim()) {
    return res.status(401).json({ error: "Unauthorized" });
  }

  try {
    await initFleetSchema();
    for (const d of DEMO_DRIVERS) {
      const passwordArgon2 = await argon2Hash(d.password, { memoryCost: 65536, timeCost: 3, parallelism: 1 });
      await upsertFleetDriver({
        id: d.id,
        name: d.name,
        username: d.username,
        passwordArgon2,
        pinArgon2: null,
        embeddingsEnc: null,
        isEnrolled: false,
      });
    }
    return res.json({
      ok: true,
      message: "Demo drivers seeded",
      drivers: DEMO_DRIVERS.map(d => ({ name: d.name, username: d.username, password: d.password })),
    });
  } catch (err) {
    console.error("seed-demo.js error:", err);
    return res.status(500).json({ error: "Server error" });
  }
}

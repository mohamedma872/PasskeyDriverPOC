import { neon } from "@neondatabase/serverless";

const sql = neon(process.env.DATABASE_URL);

const FAKE_DRIVERS = [
  { id: "drv_ahm001", name: "Ahmed Hassan",    username: "ahmed.hassan",    password: "xK7m2p", pin: "4821" },
  { id: "drv_omr002", name: "Omar Khaled",     username: "omar.khaled",     password: "Rn9v4w", pin: "3156" },
  { id: "drv_sar003", name: "Sara Ahmed",      username: "sara.ahmed",      password: "Lp3j8q", pin: "7432" },
  { id: "drv_moh004", name: "Mohamed Ali",     username: "mohamed.ali",     password: "Wk5n1r", pin: "9074" },
  { id: "drv_fat005", name: "Fatima Youssef",  username: "fatima.youssef",  password: "Bh6m3t", pin: "2589" },
];

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });
  try {
    for (const d of FAKE_DRIVERS) {
      await sql`
        INSERT INTO drivers (driver_id, name, username, password, pin)
        VALUES (${d.id}, ${d.name}, ${d.username}, ${d.password}, ${d.pin})
        ON CONFLICT (driver_id) DO NOTHING
      `;
    }
    res.status(200).json({ success: true, seeded: FAKE_DRIVERS.length });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
}

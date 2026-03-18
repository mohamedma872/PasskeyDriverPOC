import { neon } from "@neondatabase/serverless";

const sql = neon(process.env.DATABASE_URL);

export async function initSchema() {
  await sql`
    CREATE TABLE IF NOT EXISTS drivers (
      driver_id      TEXT PRIMARY KEY,
      name           TEXT NOT NULL,
      username       TEXT NOT NULL,
      password       TEXT,
      pin            TEXT,
      card_issued_at TIMESTAMP,
      created_at     TIMESTAMP DEFAULT NOW(),
      updated_at     TIMESTAMP DEFAULT NOW()
    )
  `;
  await sql`ALTER TABLE drivers ADD COLUMN IF NOT EXISTS password TEXT`;
  await sql`ALTER TABLE drivers ADD COLUMN IF NOT EXISTS pin TEXT`;
  await sql`ALTER TABLE drivers ADD COLUMN IF NOT EXISTS card_issued_at TIMESTAMP`;
}

export async function getAllDrivers() {
  const rows = await sql`
    SELECT driver_id, name, username, card_issued_at, created_at
    FROM drivers ORDER BY created_at DESC
  `;
  return rows.map(r => ({
    id: r.driver_id,
    name: r.name,
    username: r.username,
    cardIssuedAt: r.card_issued_at ? r.card_issued_at.toISOString() : null,
    createdAt: r.created_at,
  }));
}

export async function getDriverById(driverId) {
  const rows = await sql`
    SELECT driver_id, name, username, password, pin, card_issued_at, created_at
    FROM drivers WHERE driver_id = ${driverId}
  `;
  if (!rows[0]) return null;
  const r = rows[0];
  return {
    id: r.driver_id,
    name: r.name,
    username: r.username,
    password: r.password,
    pin: r.pin,
    cardIssuedAt: r.card_issued_at ? r.card_issued_at.toISOString() : null,
    createdAt: r.created_at,
  };
}

export async function markCardIssued(driverId) {
  await sql`
    UPDATE drivers SET card_issued_at = NOW(), updated_at = NOW()
    WHERE driver_id = ${driverId}
  `;
}

export async function createDriver(driverId, name, username, password, pin) {
  await sql`
    INSERT INTO drivers (driver_id, name, username, password, pin)
    VALUES (${driverId}, ${name}, ${username}, ${password}, ${pin})
  `;
}

export async function verifyDriverPin(driverId, pin) {
  const rows = await sql`SELECT pin FROM drivers WHERE driver_id = ${driverId}`;
  if (!rows[0]) return false;
  return rows[0].pin === pin;
}

export async function verifyDriverPassword(username, password) {
  const rows = await sql`
    SELECT driver_id, name FROM drivers
    WHERE username = ${username} AND password = ${password}
  `;
  if (!rows[0]) return null;
  return { id: rows[0].driver_id, name: rows[0].name, username };
}

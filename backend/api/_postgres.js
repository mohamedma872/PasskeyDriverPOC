/**
 * PostgreSQL (Neon) — credentials + drivers
 *
 * Why PostgreSQL for credentials?
 *   - Structured relational data
 *   - Public keys are binary (BYTEA) — better in SQL than Redis strings
 *   - Easy to query by driver, audit, report
 *   - Credentials are permanent (no TTL needed)
 *   - sign_count for replay protection
 *
 * Why Neon specifically?
 *   - Serverless PostgreSQL — uses HTTP, not TCP
 *   - Works in Vercel serverless functions (no connection pool issues)
 *   - Free tier: 0.5GB, plenty for a demo
 *
 * Env var (set by Vercel + Neon integration):
 *   DATABASE_URL
 */
import { neon } from "@neondatabase/serverless";

const sql = neon(process.env.DATABASE_URL);

// ── Schema setup (call once on first deploy) ──

export async function initSchema() {
  await sql`
    CREATE TABLE IF NOT EXISTS drivers (
      driver_id   TEXT PRIMARY KEY,
      name        TEXT NOT NULL,
      username    TEXT NOT NULL,
      created_at  TIMESTAMP DEFAULT NOW(),
      updated_at  TIMESTAMP DEFAULT NOW()
    )
  `;

  await sql`
    CREATE TABLE IF NOT EXISTS credentials (
      credential_id  TEXT PRIMARY KEY,
      driver_id      TEXT NOT NULL REFERENCES drivers(driver_id),
      public_key     BYTEA NOT NULL,
      sign_count     INTEGER NOT NULL DEFAULT 0,
      created_at     TIMESTAMP DEFAULT NOW(),
      last_used_at   TIMESTAMP DEFAULT NOW()
    )
  `;

  await sql`
    CREATE INDEX IF NOT EXISTS idx_credentials_driver
    ON credentials(driver_id)
  `;
}

// ── Drivers ──

export async function upsertDriver(driverId, name, username) {
  await sql`
    INSERT INTO drivers (driver_id, name, username, updated_at)
    VALUES (${driverId}, ${name}, ${username}, NOW())
    ON CONFLICT (driver_id)
    DO UPDATE SET name = ${name}, username = ${username}, updated_at = NOW()
  `;
}

export async function getDriver(driverId) {
  const rows = await sql`
    SELECT * FROM drivers WHERE driver_id = ${driverId}
  `;
  return rows[0] || null;
}

// ── Credentials ──

export async function storeCredential(credentialId, driverId, publicKeyBuffer, signCount) {
  // Convert Buffer to hex string for BYTEA storage
  const publicKeyHex = `\\x${publicKeyBuffer.toString("hex")}`;
  await sql`
    INSERT INTO credentials (credential_id, driver_id, public_key, sign_count)
    VALUES (${credentialId}, ${driverId}, ${publicKeyHex}, ${signCount})
  `;
}

export async function getCredential(credentialId) {
  const rows = await sql`
    SELECT credential_id, driver_id, public_key, sign_count
    FROM credentials
    WHERE credential_id = ${credentialId}
  `;
  if (!rows[0]) return null;

  const row = rows[0];
  return {
    credentialId: row.credential_id,
    driverId: row.driver_id,
    publicKey: row.public_key,   // Neon returns BYTEA as Buffer automatically
    signCount: row.sign_count,
  };
}

export async function updateSignCount(credentialId, newSignCount) {
  await sql`
    UPDATE credentials
    SET sign_count = ${newSignCount}, last_used_at = NOW()
    WHERE credential_id = ${credentialId}
  `;
}

export async function getCredentialIdsByDriver(driverId) {
  const rows = await sql`
    SELECT credential_id FROM credentials WHERE driver_id = ${driverId}
  `;
  return rows.map((r) => r.credential_id);
}

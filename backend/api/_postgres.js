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

// ── Fleet drivers (face + PIN auth) ──

export async function initFleetSchema() {
  await sql`
    CREATE TABLE IF NOT EXISTS fleet_drivers (
      id              TEXT PRIMARY KEY,
      name            TEXT NOT NULL,
      pin_argon2      TEXT,
      embeddings_enc  TEXT,
      is_enrolled     BOOLEAN NOT NULL DEFAULT false,
      created_at      TIMESTAMP DEFAULT NOW(),
      updated_at      TIMESTAMP DEFAULT NOW()
    )
  `;
  // Migrations for tables created in earlier versions
  await sql`ALTER TABLE fleet_drivers DROP COLUMN IF EXISTS pin_hash`;
  // Rename pin_bcrypt → pin_argon2 only if pin_bcrypt still exists
  await sql`
    DO $$
    BEGIN
      IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'fleet_drivers' AND column_name = 'pin_bcrypt'
      ) THEN
        ALTER TABLE fleet_drivers RENAME COLUMN pin_bcrypt TO pin_argon2;
      END IF;
    END $$
  `;
  await sql`ALTER TABLE fleet_drivers ADD COLUMN IF NOT EXISTS pin_argon2 TEXT`;
}

export async function lookupById(id) {
  const rows = await sql`
    SELECT id, name, pin_argon2, embeddings_enc, is_enrolled
    FROM fleet_drivers WHERE id = ${id}
  `;
  if (!rows[0]) return null;
  const r = rows[0];
  return { id: r.id, name: r.name, pinArgon2: r.pin_argon2, embeddingsEnc: r.embeddings_enc, isEnrolled: r.is_enrolled };
}

export async function listEnrolledWithEmbeddings() {
  const rows = await sql`
    SELECT id, name, embeddings_enc FROM fleet_drivers
    WHERE is_enrolled = true AND embeddings_enc IS NOT NULL
  `;
  return rows.map((r) => ({ id: r.id, name: r.name, embeddingsEnc: r.embeddings_enc }));
}

export async function listFleetDrivers() {
  const rows = await sql`
    SELECT id, name, is_enrolled FROM fleet_drivers ORDER BY name ASC
  `;
  return rows.map((r) => ({ id: r.id, name: r.name, isEnrolled: r.is_enrolled }));
}

export async function upsertFleetDriver({ id, name, pinArgon2, embeddingsEnc, isEnrolled }) {
  await sql`
    INSERT INTO fleet_drivers (id, name, pin_argon2, embeddings_enc, is_enrolled, updated_at)
    VALUES (${id}, ${name}, ${pinArgon2 ?? null}, ${embeddingsEnc ?? null}, ${isEnrolled}, NOW())
    ON CONFLICT (id)
    DO UPDATE SET
      name = ${name},
      pin_argon2 = ${pinArgon2 ?? null},
      embeddings_enc = COALESCE(${embeddingsEnc ?? null}, fleet_drivers.embeddings_enc),
      is_enrolled = ${isEnrolled},
      updated_at = NOW()
  `;
}

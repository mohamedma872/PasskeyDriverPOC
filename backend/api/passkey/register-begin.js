import { generateRegistrationOptions } from "@simplewebauthn/server";
import { storeChallenge } from "../_redis.js";           // challenge → Redis (TTL)
import { upsertDriver, getCredentialIdsByDriver } from "../_postgres.js";  // driver → Postgres
import { randomUUID } from "crypto";

const RP_ID = process.env.RP_ID || "passkeydriver-poc.web.app";
const RP_NAME = "Driver Passkey App";

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });

  const { driverId, username, displayName } = req.body;
  if (!driverId || !username || !displayName) {
    return res.status(400).json({ error: "driverId, username, displayName required" });
  }

  // Save driver to PostgreSQL
  await upsertDriver(driverId, displayName, username);

  // Get existing credential IDs from PostgreSQL (exclude from registration)
  const existingIds = await getCredentialIdsByDriver(driverId);

  const options = await generateRegistrationOptions({
    rpName: RP_NAME,
    rpID: RP_ID,
    userID: new TextEncoder().encode(driverId),
    userName: username,
    userDisplayName: displayName,
    attestationType: "none",
    authenticatorSelection: {
      authenticatorAttachment: "platform",
      residentKey: "required",
      requireResidentKey: true,
      userVerification: "required",
    },
    excludeCredentials: existingIds.map((id) => ({ id, type: "public-key" })),
  });

  // Store challenge in Redis with 60s TTL
  const challengeId = randomUUID();
  await storeChallenge(challengeId, options.challenge, driverId);

  return res.json({ challengeId, options });
}

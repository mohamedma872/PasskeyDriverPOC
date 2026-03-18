import { verifyRegistrationResponse } from "@simplewebauthn/server";
import { getAndDeleteChallenge } from "../_redis.js";        // challenge ← Redis (consumed)
import { storeCredential } from "../_postgres.js";            // public key → PostgreSQL

const RP_ID = process.env.RP_ID || "passkey-backend-tau.vercel.app";

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });

  const { challengeId, driverId, response } = req.body;
  if (!challengeId || !driverId || !response) {
    return res.status(400).json({ error: "challengeId, driverId, response required" });
  }

  // Get challenge from Redis (deleted after read — one-time use)
  const stored = await getAndDeleteChallenge(challengeId);
  if (!stored) {
    return res.status(400).json({ error: "Challenge expired or not found" });
  }

  // Real cryptographic verification
  const verification = await verifyRegistrationResponse({
    response,
    expectedChallenge: stored.challenge,
    expectedOrigin: [
      `https://${RP_ID}`,
      `android:apk-key-hash:${process.env.APK_KEY_HASH || ""}`,
    ].filter(Boolean),
    expectedRPID: RP_ID,
    requireUserVerification: true,
  });

  if (!verification.verified || !verification.registrationInfo) {
    return res.status(400).json({ error: "Verification failed" });
  }

  const { credential } = verification.registrationInfo;

  // Store public key permanently in PostgreSQL
  await storeCredential(
    credential.id,
    driverId,
    Buffer.from(credential.publicKey),
    credential.counter
  );

  return res.json({ verified: true, credentialId: credential.id });
}

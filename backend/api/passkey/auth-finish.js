import { verifyAuthenticationResponse } from "@simplewebauthn/server";
import { getAndDeleteChallenge } from "../_redis.js";              // challenge ← Redis
import { getCredential, updateSignCount } from "../_postgres.js";   // public key ← PostgreSQL

const RP_ID = process.env.RP_ID || "passkeydriver-poc.web.app";

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });

  const { challengeId, response } = req.body;
  if (!challengeId || !response) {
    return res.status(400).json({ error: "challengeId, response required" });
  }

  // Get challenge from Redis (deleted after read)
  const stored = await getAndDeleteChallenge(challengeId);
  if (!stored) {
    return res.status(400).json({ error: "Challenge expired or not found" });
  }

  // Get public key from PostgreSQL
  const credential = await getCredential(response.id);
  if (!credential) {
    return res.status(400).json({ error: "Unknown credential" });
  }

  // Real cryptographic signature verification
  const verification = await verifyAuthenticationResponse({
    response,
    expectedChallenge: stored.challenge,
    expectedOrigin: [
      `https://${RP_ID}`,
      `android:apk-key-hash:${process.env.APK_KEY_HASH || ""}`,
    ].filter(Boolean),
    expectedRPID: RP_ID,
    requireUserVerification: true,
    credential: {
      id: credential.credentialId,
      publicKey: new Uint8Array(credential.publicKey),
      counter: credential.signCount,
    },
  });

  if (!verification.verified) {
    return res.status(400).json({ error: "Authentication failed" });
  }

  // Update sign count in PostgreSQL (replay attack protection)
  await updateSignCount(credential.credentialId, verification.authenticationInfo.newCounter);

  return res.json({ verified: true, driverId: credential.driverId });
}

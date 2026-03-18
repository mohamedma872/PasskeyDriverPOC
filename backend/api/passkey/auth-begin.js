import { generateAuthenticationOptions } from "@simplewebauthn/server";
import { storeChallenge } from "../_redis.js";   // challenge → Redis (TTL)
import { randomUUID } from "crypto";

const RP_ID = process.env.RP_ID || "passkeydriver-poc.vercel.app";

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });

  const { credentialIds } = req.body || {};

  const options = await generateAuthenticationOptions({
    rpID: RP_ID,
    userVerification: "required",
    allowCredentials: credentialIds?.map((id) => ({
      id,
      type: "public-key",
      transports: ["internal"],
    })),
  });

  // Store challenge in Redis with 60s TTL
  const challengeId = randomUUID();
  await storeChallenge(challengeId, options.challenge);

  return res.json({ challengeId, options });
}

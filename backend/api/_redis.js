/**
 * Redis (Upstash) — challenges only
 *
 * Why Redis for challenges?
 *   - Built-in TTL: challenge auto-deleted after 60s, zero cleanup needed
 *   - One-time use: getdel reads AND deletes in one atomic operation
 *   - Fast: challenges are read/written on every login attempt
 *
 * Env vars (set automatically by Vercel + Upstash integration):
 *   KV_REST_API_URL
 *   KV_REST_API_TOKEN
 */
import { Redis } from "@upstash/redis";

const redis = new Redis({
  url: process.env.KV_REST_API_URL,
  token: process.env.KV_REST_API_TOKEN,
});

const CHALLENGE_TTL_SECONDS = 60;

export async function storeChallenge(challengeId, challenge, driverId = null) {
  await redis.set(
    `challenge:${challengeId}`,
    JSON.stringify({ challenge, driverId }),
    { ex: CHALLENGE_TTL_SECONDS }
  );
}

// getdel = atomic read + delete (one-time use, prevents replay attacks)
export async function getAndDeleteChallenge(challengeId) {
  const raw = await redis.getdel(`challenge:${challengeId}`);
  if (!raw) return null;
  return typeof raw === "string" ? JSON.parse(raw) : raw;
}

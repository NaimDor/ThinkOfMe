import Anthropic from "@anthropic-ai/sdk";
import { defineSecret } from "firebase-functions/params";
import { HttpsError } from "firebase-functions/v2/https";
import { db } from "./firebase";

/** Stored in Secret Manager by the deploy workflow (a placeholder until a key is configured). */
export const anthropicKey = defineSecret("ANTHROPIC_API_KEY");

/** Haiku reads products, tags, screenshots and store pages well at a fraction of a cent per call. */
export const READER_MODEL = "claude-haiku-5-5";

/** A client, or null when no real key is configured (the AI features then stay off). */
export function aiClient(): Anthropic | null {
  const key = anthropicKey.value();
  return key.startsWith("sk-") ? new Anthropic({ apiKey: key, timeout: 45_000, maxRetries: 1 }) : null;
}

/** Counts one use of `kind` today and throws once the user is past `limit`. */
export async function countDailyUse(kind: string, uid: string, limit: number): Promise<void> {
  const day = new Date().toISOString().slice(0, 10);
  const bucket = db.collection("rateLimits").doc(`${kind}_${uid}_${day}`);
  const count = await db.runTransaction(async (tx) => {
    const snap = await tx.get(bucket);
    const next = ((snap.get("count") as number | undefined) ?? 0) + 1;
    tx.set(bucket, { count: next, expiresAt: new Date(Date.now() + 2 * 86_400_000) }, { merge: true });
    return next;
  });
  if (count > limit) throw new HttpsError("resource-exhausted", "rate-limited");
}

import Anthropic from "@anthropic-ai/sdk";
import { zodOutputFormat } from "@anthropic-ai/sdk/helpers/zod";
import { logger } from "firebase-functions";
import { defineSecret } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db, ENFORCE_APP_CHECK, REGION } from "../lib/firebase";
import { cleanPhotoInfo, languageName, PhotoInfoSchema, sniffImage, SYSTEM_PROMPT } from "../lib/photoInfo";

const anthropicKey = defineSecret("ANTHROPIC_API_KEY");

/** Photos are downscaled on the phone first; anything bigger than this is not one of ours. */
const MAX_IMAGE_BYTES = 3 * 1024 * 1024;
const DAILY_LIMIT = 40;

/**
 * Reads a wish photo (product, price tag, store screenshot...) and returns the
 * details the app can prefill: title, brand, store, price, link, and so on.
 */
export const analyzePhoto = onCall(
  {
    region: REGION,
    memory: "512MiB",
    timeoutSeconds: 60,
    enforceAppCheck: ENFORCE_APP_CHECK,
    invoker: "public",
    secrets: [anthropicKey],
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
    const apiKey = anthropicKey.value();
    if (!apiKey.startsWith("sk-")) throw new HttpsError("failed-precondition", "not-configured");
    const image = request.data?.image;
    if (typeof image !== "string" || image.length < 100) throw new HttpsError("invalid-argument", "invalid-image");

    let media: ReturnType<typeof sniffImage>;
    try {
      media = sniffImage(image);
    } catch {
      throw new HttpsError("invalid-argument", "invalid-image");
    }
    if (media.bytes > MAX_IMAGE_BYTES) throw new HttpsError("invalid-argument", "image-too-large");

    // Each photo costs money to read, so keep a per-user daily cap.
    const day = new Date().toISOString().slice(0, 10);
    const bucket = db.collection("rateLimits").doc(`photo_${uid}_${day}`);
    const count = await db.runTransaction(async (tx) => {
      const snap = await tx.get(bucket);
      const next = ((snap.get("count") as number | undefined) ?? 0) + 1;
      tx.set(bucket, { count: next, expiresAt: new Date(Date.now() + 2 * 86_400_000) }, { merge: true });
      return next;
    });
    if (count > DAILY_LIMIT) throw new HttpsError("resource-exhausted", "rate-limited");

    const client = new Anthropic({ apiKey, timeout: 45_000, maxRetries: 1 });
    try {
      const message = await client.messages.parse({
        // Haiku reads products, tags and screenshots well at a fraction of a cent per photo.
        model: "claude-haiku-5-5",
        max_tokens: 4096,
        // Reading a photo is a simple extraction task: low effort keeps it fast and cheap.
        output_config: { effort: "low", format: zodOutputFormat(PhotoInfoSchema) },
        system: SYSTEM_PROMPT,
        messages: [
          {
            role: "user",
            content: [
              { type: "image", source: { type: "base64", media_type: media.mediaType, data: image } },
              { type: "text", text: `The user's language is ${languageName(request.data?.locale)}.` },
            ],
          },
        ],
      });

      if (message.stop_reason === "refusal" || !message.parsed_output) {
        logger.info("photo analysis gave no result", { stopReason: message.stop_reason });
        throw new HttpsError("failed-precondition", "no-result");
      }
      return cleanPhotoInfo(message.parsed_output);
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      if (error instanceof Anthropic.RateLimitError) throw new HttpsError("resource-exhausted", "busy");
      if (error instanceof Anthropic.APIError) {
        logger.error("photo analysis API error", { status: error.status, message: error.message });
        throw new HttpsError("unavailable", "analysis-failed");
      }
      logger.error("photo analysis failed", { error: String(error) });
      throw new HttpsError("unavailable", "analysis-failed");
    }
  },
);

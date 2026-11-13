import Anthropic from "@anthropic-ai/sdk";
import { zodOutputFormat } from "@anthropic-ai/sdk/helpers/zod";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { randomUUID } from "node:crypto";
import { aiClient, anthropicKey, countDailyUse, READER_MODEL } from "../lib/ai";
import { db, ENFORCE_APP_CHECK, REGION } from "../lib/firebase";
import { LINK_SYSTEM_PROMPT, LinkInfoSchema, linkPrompt } from "../lib/linkInfo";
import { languageName } from "../lib/photoInfo";
import { fetchImage, fetchPage, LinkMetadata, pageDigest, parseProductMetadata, UnfurlError } from "../lib/unfurl";

const HOURLY_LIMIT = 60;
const AI_DAILY_LIMIT = 60;
const COPIED_IMAGES = 3;
const EXTENSIONS: Record<string, string> = { "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp", "image/gif": "gif", "image/avif": "avif" };

interface LinkResult extends LinkMetadata {
  note?: string;
}

/**
 * Fetch a product page and return title, photos, price, store and a note for a new wish.
 * Standard page metadata comes first; when it is incomplete, the page text is read by
 * AI. With a wishId, the photos are copied into the wish's storage so they keep working.
 */
export const unfurlLink = onCall(
  { region: REGION, memory: "512MiB", timeoutSeconds: 60, enforceAppCheck: ENFORCE_APP_CHECK, invoker: "public", secrets: [anthropicKey] },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
    const url = request.data?.url;
    if (typeof url !== "string" || url.length > 2048) throw new HttpsError("invalid-argument", "invalid-url");
    const wishId = typeof request.data?.wishId === "string" && /^[A-Za-z0-9_-]{1,64}$/.test(request.data.wishId) ? request.data.wishId : null;

    // Simple per-user rate limit on fetching pages.
    const bucket = db.collection("rateLimits").doc(`unfurl_${uid}_${Math.floor(Date.now() / 3_600_000)}`);
    const count = await db.runTransaction(async (tx) => {
      const snap = await tx.get(bucket);
      const next = ((snap.get("count") as number | undefined) ?? 0) + 1;
      tx.set(bucket, { count: next, expiresAt: new Date(Date.now() + 2 * 3_600_000) }, { merge: true });
      return next;
    });
    if (count > HOURLY_LIMIT) throw new HttpsError("resource-exhausted", "rate-limited");

    let page: { html: string; url: string };
    try {
      page = await fetchPage(url);
    } catch (error) {
      if (error instanceof UnfurlError) throw new HttpsError("failed-precondition", error.message);
      logger.info("unfurl failed", { error: String(error) });
      throw new HttpsError("unavailable", "fetch-failed");
    }

    const result: LinkResult = parseProductMetadata(page.html, page.url);
    if (!result.title || !result.price || result.imageUrls.length === 0) {
      await fillWithAi(result, page, uid, request.data?.locale).catch((error) => logger.info("link AI fallback failed", { error: String(error) }));
    }
    if (wishId && result.imageUrls.length > 0) {
      result.imageUrls = await copyImages(result.imageUrls, uid, wishId);
    }
    return result;
  },
);

/** Fills what the page metadata missed from an AI read of the page text. */
async function fillWithAi(result: LinkResult, page: { html: string; url: string }, uid: string, locale: unknown): Promise<void> {
  const client = aiClient();
  if (!client) return;
  await countDailyUse("linkai", uid, AI_DAILY_LIMIT);
  const digest = pageDigest(page.html, page.url, result.imageUrls);
  if (digest.text.length < 40) return;

  let info;
  try {
    const message = await client.messages.parse({
      model: READER_MODEL,
      max_tokens: 4096,
      output_config: { effort: "low", format: zodOutputFormat(LinkInfoSchema) },
      system: LINK_SYSTEM_PROMPT,
      messages: [{ role: "user", content: linkPrompt(page.url, digest.text, digest.images, languageName(locale)) }],
    });
    if (message.stop_reason === "refusal" || !message.parsed_output) return;
    info = message.parsed_output;
  } catch (error) {
    if (error instanceof Anthropic.APIError) logger.warn("link AI API error", { status: error.status, message: error.message });
    return;
  }
  if (!info.isProduct) return;

  const text = (value: string | null, max: number) => value?.trim().slice(0, max) || undefined;
  result.title ??= text(info.title, 140) ?? text([info.brand, info.title].filter(Boolean).join(" "), 140);
  if (!result.price && typeof info.priceAmount === "number" && info.priceAmount > 0 && info.priceCurrency && /^[A-Za-z]{3}$/.test(info.priceCurrency)) {
    result.price = { amount: info.priceAmount, currency: info.priceCurrency.toUpperCase() };
  }
  if (info.store && (!result.siteName || /\.[a-z]{2,}$/i.test(result.siteName))) result.siteName = text(info.store, 80);
  result.note = text(info.note, 300);
  const chosen = info.imageIndex != null ? digest.images[info.imageIndex] : undefined;
  if (chosen) result.imageUrls = [chosen, ...result.imageUrls.filter((image) => image !== chosen)];
}

/** Copies the first photos into the wish's folder so they load reliably and privately. */
async function copyImages(urls: string[], uid: string, wishId: string): Promise<string[]> {
  const bucket = getStorage().bucket();
  const copies = await Promise.all(
    urls.slice(0, COPIED_IMAGES).map(async (source) => {
      const image = await fetchImage(source);
      if (!image) return null;
      const token = randomUUID();
      const path = `users/${uid}/wishes/${wishId}/link-${randomUUID()}.${EXTENSIONS[image.contentType] ?? "jpg"}`;
      try {
        await bucket.file(path).save(image.body, {
          resumable: false,
          contentType: image.contentType,
          metadata: { cacheControl: "public, max-age=31536000", metadata: { firebaseStorageDownloadTokens: token } },
        });
      } catch (error) {
        logger.warn("image copy failed", { error: String(error) });
        return null;
      }
      return `https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/${encodeURIComponent(path)}?alt=media&token=${token}`;
    }),
  );
  const saved = copies.filter((copy): copy is string => copy !== null);
  return saved.length > 0 ? saved : urls;
}

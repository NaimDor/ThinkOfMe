import { FieldValue } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onUserDeleted } from "firebase-functions/v2/identity";
import { auth, db, REGION } from "../lib/firebase";
import { unfurl, UnfurlError } from "../lib/unfurl";

/**
 * Remove everything a user owns. Idempotent: runs from the explicit
 * "delete my account" action and again from the auth deletion trigger.
 */
export async function purgeUserData(uid: string): Promise<void> {
  // Leave every circle (ownership passes on, empty circles go away).
  const circles = await db.collection("circles").where("memberIds", "array-contains", uid).get();
  for (const circle of circles.docs) {
    const members = ((circle.get("memberIds") as string[]) ?? []).filter((id) => id !== uid);
    if (members.length === 0) {
      await circle.ref.delete();
      continue;
    }
    const admins = ((circle.get("adminIds") as string[]) ?? []).filter((id) => id !== uid);
    const ownerId = circle.get("ownerId") === uid ? (admins[0] ?? members[0]!) : (circle.get("ownerId") as string);
    await circle.ref.update({
      memberIds: members,
      adminIds: admins.includes(ownerId) ? admins : [...admins, ownerId],
      ownerId,
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  const deleteWhere = async (collection: string, field: string): Promise<void> => {
    const snap = await db.collection(collection).where(field, "==", uid).get();
    await Promise.all(snap.docs.map((doc) => doc.ref.delete()));
  };
  await deleteWhere("wishes", "ownerId"); // their claims are removed by the wish trigger
  await deleteWhere("claims", "claimerId");
  await deleteWhere("occasions", "ownerId");
  await db.recursiveDelete(db.collection("users").doc(uid));
  await db.collection("connections").doc(uid).delete();

  try {
    await getStorage().bucket().deleteFiles({ prefix: `users/${uid}/` });
  } catch (error) {
    logger.warn("storage cleanup failed", { uid, error: String(error) });
  }
}

/** "Delete my account" from settings. Required by App Store and Play policy. */
export const deleteAccount = onCall({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
  await purgeUserData(uid);
  await auth.deleteUser(uid);
  return { ok: true };
});

/** Safety net when an account is deleted from the console or elsewhere. */
export const onAccountDeleted = onUserDeleted({ region: REGION }, async (event) => {
  await purgeUserData(event.data.uid);
});

/** Fetch a product page and return title, images and price for a new wish. */
export const unfurlLink = onCall({ region: REGION, memory: "512MiB", timeoutSeconds: 20 }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
  const url = request.data?.url;
  if (typeof url !== "string" || url.length > 2048) throw new HttpsError("invalid-argument", "invalid-url");

  // Simple per-user rate limit: 60 previews per hour.
  const bucket = db.collection("rateLimits").doc(`unfurl_${uid}_${Math.floor(Date.now() / 3_600_000)}`);
  const count = await db.runTransaction(async (tx) => {
    const snap = await tx.get(bucket);
    const next = ((snap.get("count") as number | undefined) ?? 0) + 1;
    tx.set(bucket, { count: next, expiresAt: new Date(Date.now() + 2 * 3_600_000) }, { merge: true });
    return next;
  });
  if (count > 60) throw new HttpsError("resource-exhausted", "rate-limited");

  try {
    return await unfurl(url);
  } catch (error) {
    if (error instanceof UnfurlError) throw new HttpsError("failed-precondition", error.message);
    logger.info("unfurl failed", { error: String(error) });
    throw new HttpsError("unavailable", "fetch-failed");
  }
});

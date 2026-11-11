import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { db, REGION } from "../lib/firebase";
import { notifyUser } from "../lib/notify";
import { loadCircles, refreshConnections, resyncCircleItems, syncViewers } from "../lib/sync";
import { sameIds } from "../lib/visibility";

const MATERIAL_WISH_FIELDS = ["title", "urls", "price", "note", "quantity", "store"] as const;

/** Circle membership changed: refresh connections and re-derive visibility. */
export const onCircleWritten = onDocumentWritten({ document: "circles/{circleId}", region: REGION }, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  const beforeMembers = (before?.memberIds as string[] | undefined) ?? [];
  const afterMembers = (after?.memberIds as string[] | undefined) ?? [];
  if (before && after && sameIds(beforeMembers, afterMembers)) return;

  const circleId = event.params.circleId;
  await refreshConnections([...beforeMembers, ...afterMembers]);
  await resyncCircleItems(circleId);

  if (!after) {
    // Circle deleted: detach it from items and remove its activity feed.
    for (const collection of ["wishes", "occasions"] as const) {
      const items = await db.collection(collection).where("visibleCircleIds", "array-contains", circleId).get();
      await Promise.all(items.docs.map((doc) => doc.ref.update({ visibleCircleIds: FieldValue.arrayRemove(circleId) })));
    }
    await db.recursiveDelete(db.collection("circles").doc(circleId).collection("activity"));
  }
});

/**
 * Wish changed: keep viewers in sync, tell buyers when something they are
 * getting changed or disappeared, and post claim-free activity.
 */
export const onWishWritten = onDocumentWritten({ document: "wishes/{wishId}", region: REGION }, async (event) => {
  const wishId = event.params.wishId;
  const before = event.data?.before.data();
  const after = event.data?.after.data();

  if (!after) {
    if (!before) return;
    const claims = await db.collection("claims").where("wishId", "==", wishId).get();
    const ownerName = await displayName(String(before.ownerId));
    await Promise.all(
      claims.docs.map(async (claim) => {
        await notifyUser(String(claim.get("claimerId")), "claimedWishRemoved", () => ({ name: ownerName, wish: String(before.title) }), {
          threadId: `wish_${wishId}`,
        });
        await claim.ref.delete();
      }),
    );
    return;
  }

  const visibilityChanged = !before || !sameIds(before.visibleCircleIds as string[], after.visibleCircleIds as string[]);
  if (visibilityChanged || !Array.isArray(after.viewerIds)) {
    await syncViewers(event.data!.after.ref, after);
  }

  if (!before) {
    await postActivity(String(after.ownerId), after.visibleCircleIds as string[] | undefined, {
      type: "wishAdded",
      actorId: after.ownerId,
      wishId,
      title: after.title,
    });
    return;
  }

  const materialChange = MATERIAL_WISH_FIELDS.some((field) => JSON.stringify(before[field]) !== JSON.stringify(after[field]));
  const archived = before.status === "active" && after.status !== "active";
  if (!materialChange && !archived) return;

  const claims = await db.collection("claims").where("wishId", "==", wishId).get();
  if (claims.empty) return;
  const ownerName = await displayName(String(after.ownerId));
  await Promise.all(
    claims.docs.map((claim) =>
      notifyUser(String(claim.get("claimerId")), "claimedWishChanged", () => ({ name: ownerName, wish: String(after.title) }), {
        threadId: `wish_${wishId}`,
        wishId,
      }),
    ),
  );
});

/** Occasion changed: keep viewers in sync. */
export const onOccasionWritten = onDocumentWritten({ document: "occasions/{occasionId}", region: REGION }, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!after) return;
  const visibilityChanged = !before || !sameIds(before.visibleCircleIds as string[], after.visibleCircleIds as string[]);
  if (visibilityChanged || !Array.isArray(after.viewerIds)) {
    await syncViewers(event.data!.after.ref, after);
  }
});

async function displayName(userId: string): Promise<string> {
  const snap = await db.collection("users").doc(userId).get();
  return String(snap.get("displayName") ?? "");
}

async function postActivity(actorId: string, circleIds: string[] | undefined, item: Record<string, unknown>): Promise<void> {
  if (!circleIds?.length) return;
  try {
    const circles = await loadCircles(circleIds);
    const batch = db.batch();
    for (const circleId of circleIds) {
      // Only circles the actor belongs to.
      if (!circles.get(circleId)?.memberIds.includes(actorId)) continue;
      batch.set(db.collection("circles").doc(circleId).collection("activity").doc(), {
        ...item,
        createdAt: FieldValue.serverTimestamp(),
      });
    }
    await batch.commit();
  } catch (error) {
    // A missing circle must not fail the trigger.
    logger.warn("activity write failed", { error: String(error) });
  }
}

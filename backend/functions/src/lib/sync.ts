import { FieldValue, type DocumentReference } from "firebase-admin/firestore";
import { db } from "./firebase";
import { type CircleMembership, computeConnections, computeViewerIds, sameIds } from "./visibility";

export async function loadCircles(ids: readonly string[]): Promise<Map<string, CircleMembership | undefined>> {
  const unique = [...new Set(ids)].filter((id) => id.length > 0);
  const map = new Map<string, CircleMembership | undefined>();
  if (unique.length === 0) return map;
  const snapshots = await db.getAll(...unique.map((id) => db.collection("circles").doc(id)));
  for (const snap of snapshots) {
    map.set(snap.id, snap.exists ? { id: snap.id, memberIds: (snap.get("memberIds") as string[] | undefined) ?? [] } : undefined);
  }
  return map;
}

/**
 * Recompute `viewerIds` for a wish or occasion. Writes only when the value
 * changes (so the resulting trigger is a no-op). For wishes, the claims'
 * copies are kept in sync too.
 */
export async function syncViewers(ref: DocumentReference, data: FirebaseFirestore.DocumentData): Promise<string[]> {
  const ownerId = String(data.ownerId ?? "");
  const visibleCircleIds = (data.visibleCircleIds as string[] | undefined) ?? [];
  const circles = await loadCircles(visibleCircleIds);
  const viewerIds = computeViewerIds(ownerId, visibleCircleIds, circles);

  if (!sameIds(viewerIds, data.viewerIds as string[] | undefined)) {
    await ref.update({ viewerIds });
  }
  if (ref.parent.id === "wishes") {
    await syncClaimViewers(ref.id, ownerId, viewerIds);
  }
  return viewerIds;
}

async function syncClaimViewers(wishId: string, ownerId: string, viewerIds: string[]): Promise<void> {
  const claims = await db.collection("claims").where("wishId", "==", wishId).get();
  if (claims.empty) return;
  const batch = db.batch();
  for (const claim of claims.docs) {
    // Defense in depth: the owner must never appear in a claim's viewers.
    const safeViewers = viewerIds.filter((id) => id !== ownerId);
    if (!sameIds(safeViewers, claim.get("viewerIds") as string[] | undefined)) {
      batch.update(claim.ref, { viewerIds: safeViewers, updatedAt: FieldValue.serverTimestamp() });
    }
  }
  await batch.commit();
}

/** Recompute `connections/{userId}` for each user from all their circles. */
export async function refreshConnections(userIds: readonly string[]): Promise<void> {
  await Promise.all(
    [...new Set(userIds)].map(async (userId) => {
      const circles = await db.collection("circles").where("memberIds", "array-contains", userId).get();
      const memberships = circles.docs.map((doc) => ({ id: doc.id, memberIds: (doc.get("memberIds") as string[]) ?? [] }));
      const userIdsList = computeConnections(userId, memberships);
      await db.collection("connections").doc(userId).set({ userIds: userIdsList, updatedAt: FieldValue.serverTimestamp() });
    }),
  );
}

/** Re-derive visibility for every wish and occasion shared with a circle. */
export async function resyncCircleItems(circleId: string): Promise<void> {
  for (const collection of ["wishes", "occasions"] as const) {
    const items = await db.collection(collection).where("visibleCircleIds", "array-contains", circleId).get();
    for (const item of items.docs) {
      await syncViewers(item.ref, item.data());
    }
  }
}

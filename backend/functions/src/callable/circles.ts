import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { db, REGION } from "../lib/firebase";
import {
  type CircleKind,
  generateInviteCode,
  INVITE_TTL_DAYS,
  inviteLink,
  maxMembers,
  normalizeInviteCode,
} from "../lib/invites";

const callable = { region: REGION, enforceAppCheck: false } as const;

function requireUid(request: CallableRequest): string {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
  return uid;
}

function requireString(value: unknown, field: string): string {
  if (typeof value !== "string" || value.length === 0 || value.length > 128) {
    throw new HttpsError("invalid-argument", `Missing ${field}.`);
  }
  return value;
}

/** Create a shareable invite to a circle. Admins only. */
export const createInvite = onCall(callable, async (request) => {
  const uid = requireUid(request);
  const circleId = requireString(request.data?.circleId, "circleId");
  const circleRef = db.collection("circles").doc(circleId);
  const circle = await circleRef.get();
  if (!circle.exists) throw new HttpsError("not-found", "Circle not found.");
  if (!((circle.get("adminIds") as string[]) ?? []).includes(uid)) {
    throw new HttpsError("permission-denied", "Only circle admins can invite.");
  }
  const kind = circle.get("kind") as CircleKind;
  const memberCount = ((circle.get("memberIds") as string[]) ?? []).length;
  if (memberCount >= maxMembers(kind)) throw new HttpsError("failed-precondition", "circle-full");

  for (let attempt = 0; attempt < 5; attempt++) {
    const code = generateInviteCode();
    const expiresAt = Timestamp.fromMillis(Date.now() + INVITE_TTL_DAYS * 86_400_000);
    try {
      await db.collection("invites").doc(code).create({
        circleId,
        createdBy: uid,
        createdAt: FieldValue.serverTimestamp(),
        expiresAt,
        maxUses: kind === "pair" ? 1 : 25,
        uses: 0,
      });
      return { code, url: inviteLink(code), expiresAt: expiresAt.toMillis() };
    } catch {
      // Code collision: try another one.
    }
  }
  throw new HttpsError("internal", "Could not create an invite.");
});

async function loadValidInvite(rawCode: unknown) {
  const code = normalizeInviteCode(rawCode);
  if (!code) throw new HttpsError("invalid-argument", "invalid-code");
  const invite = await db.collection("invites").doc(code).get();
  if (!invite.exists) throw new HttpsError("not-found", "invalid-code");
  const expiresAt = invite.get("expiresAt") as Timestamp;
  if (expiresAt.toMillis() < Date.now()) throw new HttpsError("failed-precondition", "expired");
  if ((invite.get("uses") as number) >= (invite.get("maxUses") as number)) {
    throw new HttpsError("failed-precondition", "used-up");
  }
  return invite;
}

/** What the invitee sees before joining. Reveals no member list. */
export const previewInvite = onCall(callable, async (request) => {
  requireUid(request);
  const invite = await loadValidInvite(request.data?.code);
  const [circle, inviter] = await Promise.all([
    db.collection("circles").doc(String(invite.get("circleId"))).get(),
    db.collection("users").doc(String(invite.get("createdBy"))).get(),
  ]);
  if (!circle.exists) throw new HttpsError("not-found", "invalid-code");
  return {
    circleName: circle.get("name"),
    kind: circle.get("kind"),
    memberCount: ((circle.get("memberIds") as string[]) ?? []).length,
    inviterName: inviter.get("displayName") ?? "",
    inviterPhotoUrl: inviter.get("photoUrl") ?? null,
  };
});

/** Join a circle with an invite code. */
export const redeemInvite = onCall(callable, async (request) => {
  const uid = requireUid(request);
  const code = normalizeInviteCode(request.data?.code);
  if (!code) throw new HttpsError("invalid-argument", "invalid-code");

  return db.runTransaction(async (tx) => {
    const inviteRef = db.collection("invites").doc(code);
    const invite = await tx.get(inviteRef);
    if (!invite.exists) throw new HttpsError("not-found", "invalid-code");
    if ((invite.get("expiresAt") as Timestamp).toMillis() < Date.now()) {
      throw new HttpsError("failed-precondition", "expired");
    }
    if ((invite.get("uses") as number) >= (invite.get("maxUses") as number)) {
      throw new HttpsError("failed-precondition", "used-up");
    }
    const circleRef = db.collection("circles").doc(String(invite.get("circleId")));
    const circle = await tx.get(circleRef);
    if (!circle.exists) throw new HttpsError("not-found", "invalid-code");
    const members = (circle.get("memberIds") as string[]) ?? [];
    if (members.includes(uid)) return { circleId: circleRef.id, alreadyMember: true };
    if (members.length >= maxMembers(circle.get("kind") as CircleKind)) {
      throw new HttpsError("failed-precondition", "circle-full");
    }
    tx.update(circleRef, { memberIds: FieldValue.arrayUnion(uid), updatedAt: FieldValue.serverTimestamp() });
    tx.update(inviteRef, { uses: FieldValue.increment(1) });
    return { circleId: circleRef.id, alreadyMember: false };
  });
});

/** Leave a circle. Ownership passes on; an empty circle is deleted. */
export const leaveCircle = onCall(callable, async (request) => {
  const uid = requireUid(request);
  const circleId = requireString(request.data?.circleId, "circleId");
  await db.runTransaction(async (tx) => {
    const ref = db.collection("circles").doc(circleId);
    const circle = await tx.get(ref);
    if (!circle.exists) return;
    const members = ((circle.get("memberIds") as string[]) ?? []).filter((id) => id !== uid);
    if (members.length === ((circle.get("memberIds") as string[]) ?? []).length) return;
    if (members.length === 0) {
      tx.delete(ref);
      return;
    }
    let admins = ((circle.get("adminIds") as string[]) ?? []).filter((id) => id !== uid);
    let ownerId = circle.get("ownerId") as string;
    if (ownerId === uid) ownerId = admins[0] ?? members[0]!;
    if (!admins.includes(ownerId)) admins = [...admins, ownerId];
    tx.update(ref, { memberIds: members, adminIds: admins, ownerId, updatedAt: FieldValue.serverTimestamp() });
  });
  return { ok: true };
});

/** Remove someone from a circle. Admins only; the owner cannot be removed. */
export const removeMember = onCall(callable, async (request) => {
  const uid = requireUid(request);
  const circleId = requireString(request.data?.circleId, "circleId");
  const userId = requireString(request.data?.userId, "userId");
  await db.runTransaction(async (tx) => {
    const ref = db.collection("circles").doc(circleId);
    const circle = await tx.get(ref);
    if (!circle.exists) throw new HttpsError("not-found", "Circle not found.");
    if (!((circle.get("adminIds") as string[]) ?? []).includes(uid)) {
      throw new HttpsError("permission-denied", "Only circle admins can remove members.");
    }
    if (circle.get("ownerId") === userId) throw new HttpsError("failed-precondition", "cannot-remove-owner");
    tx.update(ref, {
      memberIds: FieldValue.arrayRemove(userId),
      adminIds: FieldValue.arrayRemove(userId),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  return { ok: true };
});

/**
 * Pure visibility logic. Who may see a wish or an occasion is derived from the
 * circles its owner shared it with. The result is denormalized onto documents
 * as `viewerIds` so security rules and queries stay simple.
 */

export interface CircleMembership {
  readonly id: string;
  readonly memberIds: readonly string[];
}

/**
 * Everyone who can see an item owned by `ownerId` and shared with
 * `visibleCircleIds`. The owner is never included. Circles the owner is not a
 * member of are ignored, so an owner cannot push content into strangers' feeds.
 */
export function computeViewerIds(
  ownerId: string,
  visibleCircleIds: readonly string[],
  circles: ReadonlyMap<string, CircleMembership | undefined>,
): string[] {
  const viewers = new Set<string>();
  for (const circleId of visibleCircleIds) {
    const circle = circles.get(circleId);
    if (!circle || !circle.memberIds.includes(ownerId)) continue;
    for (const memberId of circle.memberIds) {
      if (memberId !== ownerId) viewers.add(memberId);
    }
  }
  return [...viewers].sort();
}

/** Everyone who shares at least one circle with `userId` (excluding the user). */
export function computeConnections(userId: string, circles: readonly CircleMembership[]): string[] {
  const connections = new Set<string>();
  for (const circle of circles) {
    if (!circle.memberIds.includes(userId)) continue;
    for (const memberId of circle.memberIds) {
      if (memberId !== userId) connections.add(memberId);
    }
  }
  return [...connections].sort();
}

/** Order-insensitive equality for id lists. */
export function sameIds(a: readonly string[] | undefined, b: readonly string[] | undefined): boolean {
  const left = [...(a ?? [])].sort();
  const right = [...(b ?? [])].sort();
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

/** Members present in exactly one of the two lists. */
export function changedMembers(before: readonly string[] | undefined, after: readonly string[] | undefined): string[] {
  const b = new Set(before ?? []);
  const a = new Set(after ?? []);
  const changed = new Set<string>();
  for (const id of b) if (!a.has(id)) changed.add(id);
  for (const id of a) if (!b.has(id)) changed.add(id);
  return [...changed].sort();
}

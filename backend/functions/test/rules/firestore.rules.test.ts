import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  updateDoc,
  where,
} from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

// Cast of characters: Maya owns a wish shared with the "family" circle.
// Dana and Noa are in the circle. Eve is a stranger.
const MAYA = "maya";
const DANA = "dana";
const NOA = "noa";
const EVE = "eve";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-thinkofme",
    firestore: { rules: readFileSync(resolve(__dirname, "../../../firestore.rules"), "utf8") },
  });
});

afterAll(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "circles/family"), {
      name: "Family", kind: "group", ownerId: MAYA, memberIds: [MAYA, DANA, NOA], adminIds: [MAYA],
    });
    await setDoc(doc(db, "wishes/headphones"), {
      ownerId: MAYA, title: "Headphones", kind: "product", priority: 2, quantity: 1, status: "active",
      visibleCircleIds: ["family"], viewerIds: [DANA, NOA],
    });
    await setDoc(doc(db, "claims/headphones_noa"), {
      wishId: "headphones", wishOwnerId: MAYA, claimerId: NOA, claimerName: "Noa",
      viewerIds: [DANA, NOA], status: "reserved", quantity: 1,
    });
    await setDoc(doc(db, "connections/maya"), { userIds: [DANA, NOA] });
    await setDoc(doc(db, "users/maya"), { displayName: "Maya", aboutMe: { shoeSize: "38" } });
  });
});

const as = (uid: string) => env.authenticatedContext(uid).firestore();

describe("the surprise guarantee", () => {
  it("never lets the wish owner read a claim on their wish", async () => {
    await assertFails(getDoc(doc(as(MAYA), "claims/headphones_noa")));
  });

  it("never lets the owner query claims on their wishes", async () => {
    await assertFails(getDocs(query(collection(as(MAYA), "claims"), where("wishOwnerId", "==", MAYA))));
    await assertFails(getDocs(query(collection(as(MAYA), "claims"), where("wishId", "==", "headphones"))));
  });

  it("lets other circle members see the claim so nobody double-buys", async () => {
    await assertSucceeds(getDoc(doc(as(DANA), "claims/headphones_noa")));
    await assertSucceeds(
      getDocs(query(collection(as(DANA), "claims"), where("viewerIds", "array-contains", DANA), where("wishOwnerId", "==", MAYA))),
    );
  });

  it("lets a claimer list their own claims", async () => {
    await assertSucceeds(getDocs(query(collection(as(NOA), "claims"), where("claimerId", "==", NOA))));
    await assertFails(getDocs(query(collection(as(MAYA), "claims"), where("claimerId", "==", NOA))));
  });

  it("rejects broad claim queries that could include the caller's own wishes", async () => {
    await assertFails(getDocs(query(collection(as(DANA), "claims"), where("viewerIds", "array-contains", DANA))));
  });

  it("hides claims from strangers", async () => {
    await assertFails(getDoc(doc(as(EVE), "claims/headphones_noa")));
  });

  it("does not let an owner claim their own wish (which would reveal claims)", async () => {
    await assertFails(
      setDoc(doc(as(MAYA), "claims/headphones_maya"), {
        wishId: "headphones", wishOwnerId: MAYA, claimerId: MAYA, viewerIds: [DANA, NOA], status: "reserved", quantity: 1,
      }),
    );
  });

  it("does not let a claimer sneak the owner into the claim's viewers", async () => {
    await assertFails(
      setDoc(doc(as(DANA), "claims/headphones_dana"), {
        wishId: "headphones", wishOwnerId: MAYA, claimerId: DANA, viewerIds: [DANA, NOA, MAYA], status: "reserved", quantity: 1,
      }),
    );
  });

  it("does not let a claimer re-target viewers after creating the claim", async () => {
    await assertFails(updateDoc(doc(as(NOA), "claims/headphones_noa"), { viewerIds: [NOA, MAYA] }));
  });
});

describe("claims", () => {
  it("lets a viewer claim a wish with the wish's exact viewers", async () => {
    await assertSucceeds(
      setDoc(doc(as(DANA), "claims/headphones_dana"), {
        wishId: "headphones", wishOwnerId: MAYA, claimerId: DANA, claimerName: "Dana", viewerIds: [DANA, NOA], status: "reserved", quantity: 1,
      }),
    );
  });

  it("rejects claims under someone else's name or id", async () => {
    await assertFails(
      setDoc(doc(as(DANA), "claims/headphones_noa2"), {
        wishId: "headphones", wishOwnerId: MAYA, claimerId: DANA, viewerIds: [DANA, NOA], status: "reserved", quantity: 1,
      }),
    );
  });

  it("rejects claims from people who cannot see the wish", async () => {
    await assertFails(
      setDoc(doc(as(EVE), "claims/headphones_eve"), {
        wishId: "headphones", wishOwnerId: MAYA, claimerId: EVE, viewerIds: [DANA, NOA], status: "reserved", quantity: 1,
      }),
    );
  });

  it("lets only the claimer update or release a claim", async () => {
    await assertSucceeds(updateDoc(doc(as(NOA), "claims/headphones_noa"), { status: "purchased" }));
    await assertFails(updateDoc(doc(as(DANA), "claims/headphones_noa"), { status: "purchased" }));
    await assertFails(deleteDoc(doc(as(DANA), "claims/headphones_noa")));
    await assertSucceeds(deleteDoc(doc(as(NOA), "claims/headphones_noa")));
  });
});

describe("wishes", () => {
  it("are visible to the owner and their viewers only", async () => {
    await assertSucceeds(getDoc(doc(as(MAYA), "wishes/headphones")));
    await assertSucceeds(getDoc(doc(as(DANA), "wishes/headphones")));
    await assertFails(getDoc(doc(as(EVE), "wishes/headphones")));
  });

  it("can be listed by viewers through viewerIds", async () => {
    await assertSucceeds(getDocs(query(collection(as(DANA), "wishes"), where("viewerIds", "array-contains", DANA))));
    await assertFails(getDocs(query(collection(as(EVE), "wishes"), where("ownerId", "==", MAYA))));
  });

  const newWish = (ownerId: string, extra: Record<string, unknown> = {}) => ({
    ownerId, title: "Cookbook", kind: "product", priority: 1, quantity: 1, status: "active",
    visibleCircleIds: ["family"], viewerIds: [], ...extra,
  });

  it("can be created by their owner with empty viewers", async () => {
    await assertSucceeds(setDoc(doc(as(DANA), "wishes/w1"), newWish(DANA)));
  });

  it("cannot be created with self-chosen viewers or for someone else", async () => {
    await assertFails(setDoc(doc(as(DANA), "wishes/w2"), newWish(DANA, { viewerIds: [EVE] })));
    await assertFails(setDoc(doc(as(DANA), "wishes/w3"), newWish(MAYA)));
  });

  it("can be edited only by their owner, never their viewers list", async () => {
    await assertSucceeds(updateDoc(doc(as(MAYA), "wishes/headphones"), { title: "Better headphones" }));
    await assertFails(updateDoc(doc(as(MAYA), "wishes/headphones"), { viewerIds: [EVE] }));
    await assertFails(updateDoc(doc(as(DANA), "wishes/headphones"), { title: "Socks" }));
  });

  it("validates fields", async () => {
    await assertFails(setDoc(doc(as(DANA), "wishes/w4"), newWish(DANA, { priority: 7 })));
    await assertFails(setDoc(doc(as(DANA), "wishes/w5"), newWish(DANA, { title: "" })));
    await assertFails(setDoc(doc(as(DANA), "wishes/w6"), newWish(DANA, { secret: true })));
  });
});

describe("circles", () => {
  it("are readable by members only", async () => {
    await assertSucceeds(getDoc(doc(as(NOA), "circles/family")));
    await assertFails(getDoc(doc(as(EVE), "circles/family")));
  });

  it("can be created containing only yourself", async () => {
    await assertSucceeds(
      setDoc(doc(as(EVE), "circles/c1"), { name: "Us", kind: "pair", ownerId: EVE, memberIds: [EVE], adminIds: [EVE] }),
    );
    await assertFails(
      setDoc(doc(as(EVE), "circles/c2"), { name: "Us", kind: "pair", ownerId: EVE, memberIds: [EVE, MAYA], adminIds: [EVE] }),
    );
  });

  it("never lets clients change membership directly", async () => {
    await assertFails(updateDoc(doc(as(MAYA), "circles/family"), { memberIds: [MAYA, DANA, NOA, EVE] }));
    await assertFails(updateDoc(doc(as(EVE), "circles/family"), { name: "Hacked" }));
    await assertSucceeds(updateDoc(doc(as(MAYA), "circles/family"), { name: "The Family" }));
  });
});

describe("profiles and server-only data", () => {
  it("shows profiles to connections only", async () => {
    await assertSucceeds(getDoc(doc(as(DANA), "users/maya")));
    await assertFails(getDoc(doc(as(EVE), "users/maya")));
  });

  it("keeps private settings private", async () => {
    await assertSucceeds(setDoc(doc(as(MAYA), "users/maya/private/settings"), { timeZone: "Asia/Jerusalem" }));
    await assertFails(getDoc(doc(as(DANA), "users/maya/private/settings")));
  });

  it("denies all client access to invites, connections of others and reminder logs", async () => {
    await assertFails(getDoc(doc(as(MAYA), "invites/ABCDEFGH")));
    await assertFails(setDoc(doc(as(MAYA), "invites/ABCDEFGH"), { circleId: "family" }));
    await assertFails(getDoc(doc(as(DANA), "connections/maya")));
    await assertFails(setDoc(doc(as(MAYA), "connections/maya"), { userIds: [EVE] }));
    await assertFails(getDoc(doc(as(MAYA), "reminderLog/x")));
  });
});

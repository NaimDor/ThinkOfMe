import { getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

if (getApps().length === 0) initializeApp();

export const db = getFirestore();
export const auth = getAuth();
export const messaging = getMessaging();

export const REGION = "europe-west1";

/**
 * Turn on once App Check is set up in the Firebase console for both apps
 * (App Attest on iOS, Play Integrity on Android): callables then reject
 * requests that don't come from the real apps.
 */
export const ENFORCE_APP_CHECK = false;

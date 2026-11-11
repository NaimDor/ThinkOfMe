import { logger } from "firebase-functions";
import { db, messaging } from "./firebase";
import { type Locale, type MessageKey, render, resolveLocale } from "./messages";

interface Device {
  id: string;
  token: string;
  locale: Locale;
}

async function devicesFor(userId: string): Promise<Device[]> {
  const snapshot = await db.collection("users").doc(userId).collection("devices").get();
  return snapshot.docs
    .map((doc) => ({ id: doc.id, token: String(doc.get("token") ?? ""), locale: resolveLocale(doc.get("locale")) }))
    .filter((device) => device.token.length > 0);
}

/**
 * Send a localized push to every device of a user. Message variables may
 * depend on the locale (e.g. occasion names), so they come from a callback.
 * Dead tokens are pruned.
 */
export async function notifyUser(
  userId: string,
  key: MessageKey,
  vars: (locale: Locale) => Record<string, string | number>,
  data: Record<string, string> = {},
): Promise<void> {
  const devices = await devicesFor(userId);
  if (devices.length === 0) return;

  const results = await messaging.sendEach(
    devices.map((device) => {
      const message = render(key, device.locale, vars(device.locale));
      return {
        token: device.token,
        notification: message,
        data: { type: key, ...data },
        apns: { payload: { aps: { sound: "default", "thread-id": data.threadId ?? key } } },
        android: { notification: { channelId: channelFor(key), tag: data.threadId } },
      };
    }),
  );

  const dead: string[] = [];
  results.responses.forEach((response, index) => {
    const code = response.error?.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
      const device = devices[index];
      if (device) dead.push(device.id);
    } else if (response.error) {
      logger.warn("push failed", { userId, code });
    }
  });
  await Promise.all(dead.map((id) => db.collection("users").doc(userId).collection("devices").doc(id).delete()));
}

function channelFor(key: MessageKey): string {
  return key.startsWith("claimed") ? "gift_updates" : "reminders";
}

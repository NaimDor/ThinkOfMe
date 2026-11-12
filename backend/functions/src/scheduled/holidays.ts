import { FieldPath, FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { formatIsoDate, localNow } from "../lib/dates";
import { db, REGION } from "../lib/firebase";
import { dueHolidayReminder, enabledHolidays, timeZonesAtHour } from "../lib/holidays";
import { notifyUser } from "../lib/notify";

const SEND_HOUR = 10;

/**
 * Hourly: users whose local clock reads 10:00 get at most one holiday reminder
 * a day (Valentine's Day, ט״ו באב, Hanukkah, Eid, 8 March…), based on the
 * holidays they turned on (or their language's defaults).
 */
export const sendHolidayReminders = onSchedule({ schedule: "every 60 minutes", region: REGION, timeoutSeconds: 540 }, async () => {
  const now = new Date();
  const zones = timeZonesAtHour(now, SEND_HOUR);
  let sent = 0;

  for (let i = 0; i < zones.length; i += 30) {
    const settings = await db.collectionGroup("private").where("timeZone", "in", zones.slice(i, i + 30)).get();
    for (const doc of settings.docs) {
      const userRef = doc.ref.parent.parent;
      if (!userRef || doc.id !== "settings") continue;
      if (doc.get("notifications.reminders") === false) continue;

      const uid = userRef.id;
      const local = localNow(now, String(doc.get("timeZone")));
      const holidays = enabledHolidays(doc.get("holidays") as string[] | undefined, doc.get("locale") as string | undefined);
      if (holidays.length === 0) continue;

      const circles = await db.collection("circles").where("memberIds", "array-contains", uid).get();
      const partnerIds = circles.docs
        .filter((c) => c.get("kind") === "pair")
        .flatMap((c) => (c.get("memberIds") as string[]).filter((m) => m !== uid));
      const hasPeople = circles.docs.some((c) => ((c.get("memberIds") as string[]) ?? []).length > 1);
      const partners = partnerIds.length
        ? (await db.collection("users").where(FieldPath.documentId(), "in", partnerIds.slice(0, 30)).get()).docs
        : [];
      const partnerNames = partners.map((d) => String(d.get("displayName") ?? ""));

      const due = dueHolidayReminder(holidays, local, { partnerNames, partnerIds: partners.map((d) => d.id), hasPeople });
      if (!due) continue;

      // One holiday reminder per user per day, idempotent across retries.
      try {
        await db.collection("reminderLog").doc(`holiday_${uid}_${formatIsoDate(local)}`).create({
          holiday: due.holiday.id,
          kind: due.kind,
          createdAt: FieldValue.serverTimestamp(),
          expiresAt: new Date(Date.now() + 60 * 86_400_000), // TTL policy deletes it
        });
      } catch {
        continue;
      }

      const key = due.kind === "self" ? "holidaySelf" : due.kind === "partner" ? "holidayPartner" : "holidayOthers";
      await notifyUser(
        uid,
        key,
        (locale) => ({ holiday: due.holiday.name[locale], days: due.days, name: due.kind === "partner" ? due.partnerName : "" }),
        {
          holidayId: due.holiday.id,
          threadId: `holiday_${due.holiday.id}`,
          // Self → your list; partner → their list; others → home.
          screen: due.kind === "self" ? "myList" : due.kind === "partner" ? "person" : "home",
          ...(due.kind === "partner" && due.partnerId ? { personId: due.partnerId } : {}),
        },
      );
      sent++;
    }
  }
  logger.info("holiday reminders run", { zones: zones.length, sent });
});

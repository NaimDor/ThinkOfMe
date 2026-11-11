import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { daysUntilNext, dueReminderOffset, formatIsoDate, localNow, type Recurrence } from "../lib/dates";
import { db, REGION } from "../lib/firebase";
import { occasionName } from "../lib/messages";
import { notifyUser } from "../lib/notify";

/** Local hour at which reminders go out in each recipient's time zone. */
const SEND_HOUR = 10;
const LOOKAHEAD_DAYS = 31;

interface Occasion {
  id: string;
  ownerId: string;
  personId: string | null;
  kind: string;
  title: string;
  month: number;
  day: number;
  year: number | null;
  recurrence: Recurrence;
  viewerIds: string[];
}

interface RecipientSettings {
  timeZone: string;
  enabled: boolean;
  selfDays?: number[];
  othersDays?: number[];
}

/**
 * Runs hourly. For every occasion in the next month, each recipient whose
 * local clock reads SEND_HOUR gets at most one reminder per offset. A log
 * document makes sends idempotent across retries and overlapping runs.
 */
export const sendReminders = onSchedule({ schedule: "every 60 minutes", region: REGION, timeoutSeconds: 540 }, async () => {
  const now = new Date();
  const occasions = await upcomingOccasions(now);
  const settingsCache = new Map<string, RecipientSettings>();
  const nameCache = new Map<string, string>();
  let sent = 0;

  for (const occasion of occasions) {
    const celebrated = occasion.personId ?? occasion.ownerId;
    const recipients = new Set<string>([celebrated, ...occasion.viewerIds]);

    for (const recipientId of recipients) {
      const settings = await recipientSettings(recipientId, settingsCache);
      if (!settings.enabled) continue;
      const local = localNow(now, settings.timeZone);
      if (local.hour !== SEND_HOUR) continue;

      const audience = recipientId === celebrated ? "self" : "others";
      const days = daysUntilNext(occasion, local);
      const offset = dueReminderOffset(days, audience, audience === "self" ? settings.selfDays : settings.othersDays);
      if (offset == null) continue;

      const logId = `${occasion.id}_${recipientId}_${formatIsoDate(local)}`;
      try {
        await db.collection("reminderLog").doc(logId).create({ createdAt: FieldValue.serverTimestamp(), offset });
      } catch {
        continue; // already sent today
      }

      const name = audience === "others" ? await displayName(celebrated, nameCache) : "";
      const key = audience === "self" ? "selfUpcoming" : offset === 0 ? "othersToday" : "othersUpcoming";
      await notifyUser(
        recipientId,
        key,
        (locale) => ({ name, days: offset, occasion: occasion.kind === "custom" ? occasion.title : occasionName(occasion.kind, locale) }),
        { occasionId: occasion.id, personId: celebrated, threadId: `occasion_${occasion.id}` },
      );
      sent++;
    }
  }
  logger.info("reminders run", { occasions: occasions.length, sent });
});

async function upcomingOccasions(now: Date): Promise<Occasion[]> {
  // Occasions in this month and the next couple of months cover a 31-day
  // lookahead in every time zone.
  const months = new Set<number>();
  for (let i = -1; i <= LOOKAHEAD_DAYS + 1; i += 7) {
    months.add(new Date(now.getTime() + i * 86_400_000).getUTCMonth() + 1);
  }
  months.add(new Date(now.getTime() + (LOOKAHEAD_DAYS + 1) * 86_400_000).getUTCMonth() + 1);
  const snapshot = await db.collection("occasions").where("month", "in", [...months]).get();
  return snapshot.docs.map((doc) => ({
    id: doc.id,
    ownerId: String(doc.get("ownerId")),
    personId: (doc.get("personId") as string | undefined) ?? null,
    kind: String(doc.get("kind") ?? "custom"),
    title: String(doc.get("title") ?? ""),
    month: Number(doc.get("month")),
    day: Number(doc.get("day")),
    year: (doc.get("year") as number | undefined) ?? null,
    recurrence: (doc.get("recurrence") as Recurrence | undefined) ?? "yearly",
    viewerIds: (doc.get("viewerIds") as string[] | undefined) ?? [],
  }));
}

async function recipientSettings(userId: string, cache: Map<string, RecipientSettings>): Promise<RecipientSettings> {
  const cached = cache.get(userId);
  if (cached) return cached;
  const snap = await db.collection("users").doc(userId).collection("private").doc("settings").get();
  const settings: RecipientSettings = {
    timeZone: String(snap.get("timeZone") ?? "UTC"),
    enabled: snap.get("notifications.reminders") !== false,
    selfDays: snap.get("notifications.selfDays") as number[] | undefined,
    othersDays: snap.get("notifications.othersDays") as number[] | undefined,
  };
  cache.set(userId, settings);
  return settings;
}

async function displayName(userId: string, cache: Map<string, string>): Promise<string> {
  const cached = cache.get(userId);
  if (cached !== undefined) return cached;
  const snap = await db.collection("users").doc(userId).get();
  const name = String(snap.get("displayName") ?? "");
  cache.set(userId, name);
  return name;
}

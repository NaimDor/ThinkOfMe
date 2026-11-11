/**
 * Calendar helpers for occasions and reminders. Everything works on plain
 * calendar dates (year/month/day) in the recipient's own time zone, so a
 * birthday is "today" for each person according to their own clock.
 */

export interface CalendarDate {
  readonly year: number;
  readonly month: number; // 1-12
  readonly day: number; // 1-31
}

export interface LocalNow extends CalendarDate {
  readonly hour: number; // 0-23
}

export type Recurrence = "yearly" | "once";

export function isLeapYear(year: number): boolean {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
}

export function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

/** Days since the epoch for a calendar date (time-zone free). */
export function toEpochDay(date: CalendarDate): number {
  return Math.floor(Date.UTC(date.year, date.month - 1, date.day) / 86_400_000);
}

/**
 * The date an occasion lands on in a given year. A 29 February occasion is
 * celebrated on 28 February in non-leap years; other impossible dates clamp
 * to the month's last day.
 */
export function occurrenceIn(year: number, month: number, day: number): CalendarDate {
  return { year, month, day: Math.min(day, daysInMonth(year, month)) };
}

/**
 * Days from `today` until the next occurrence (0 = today). Returns null for a
 * one-off occasion that has already passed.
 */
export function daysUntilNext(
  occasion: { month: number; day: number; year?: number | null; recurrence: Recurrence },
  today: CalendarDate,
): number | null {
  const todayEpoch = toEpochDay(today);
  if (occasion.recurrence === "once") {
    if (occasion.year == null) return null;
    const diff = toEpochDay(occurrenceIn(occasion.year, occasion.month, occasion.day)) - todayEpoch;
    return diff >= 0 ? diff : null;
  }
  let candidate = occurrenceIn(today.year, occasion.month, occasion.day);
  if (toEpochDay(candidate) < todayEpoch) {
    candidate = occurrenceIn(today.year + 1, occasion.month, occasion.day);
  }
  return toEpochDay(candidate) - todayEpoch;
}

/** The current calendar date and hour in an IANA time zone. */
export function localNow(instant: Date, timeZone: string): LocalNow {
  let zone = timeZone;
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: zone });
  } catch {
    zone = "UTC";
  }
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: zone,
    year: "numeric",
    month: "numeric",
    day: "numeric",
    hour: "numeric",
    hourCycle: "h23",
  }).formatToParts(instant);
  const get = (type: Intl.DateTimeFormatPartTypes): number =>
    Number(parts.find((part) => part.type === type)?.value ?? "0");
  return { year: get("year"), month: get("month"), day: get("day"), hour: get("hour") };
}

export function formatIsoDate(date: CalendarDate): string {
  const pad = (n: number): string => String(n).padStart(2, "0");
  return `${date.year}-${pad(date.month)}-${pad(date.day)}`;
}

export const DEFAULT_SELF_REMINDER_DAYS: readonly number[] = [30, 14];
export const DEFAULT_OTHERS_REMINDER_DAYS: readonly number[] = [21, 7, 2, 0];

export type ReminderAudience = "self" | "others";

/**
 * Which reminder (if any) is due for a recipient today. Returns the matching
 * offset in days, or null when nothing is due.
 */
export function dueReminderOffset(
  daysUntil: number | null,
  audience: ReminderAudience,
  offsets?: readonly number[],
): number | null {
  if (daysUntil == null) return null;
  const list = offsets ?? (audience === "self" ? DEFAULT_SELF_REMINDER_DAYS : DEFAULT_OTHERS_REMINDER_DAYS);
  return list.includes(daysUntil) ? daysUntil : null;
}

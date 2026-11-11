import { type CalendarDate, formatIsoDate, toEpochDay } from "./dates";
import { HOLIDAYS, type HolidayDef } from "./holidays.generated";
import { type Locale, resolveLocale } from "./messages";

export { HOLIDAYS, type HolidayDef };

/** Days before a holiday: nudge yourself to add wishes, then nudge others to look. */
export const HOLIDAY_SELF_DAYS = 21;
export const HOLIDAY_OTHERS_DAYS = 7;

/** The holidays a user has on: their saved choice, or the defaults for their language. */
export function enabledHolidays(saved: readonly string[] | undefined | null, locale: string | undefined | null): HolidayDef[] {
  if (Array.isArray(saved)) return HOLIDAYS.filter((h) => saved.includes(h.id));
  const lang: Locale = resolveLocale(locale);
  return HOLIDAYS.filter((h) => h.defaults.includes(lang));
}

/** Days from `today` until the holiday's next date, or null past the precomputed range. */
export function daysUntilHoliday(holiday: HolidayDef, today: CalendarDate): number | null {
  const todayIso = formatIsoDate(today);
  const next = holiday.dates.find((d) => d >= todayIso);
  if (!next) return null;
  const [y, m, d] = next.split("-").map(Number) as [number, number, number];
  return toEpochDay({ year: y, month: m, day: d }) - toEpochDay(today);
}

export interface HolidayContext {
  /** Partners from two-person circles. */
  partnerNames: string[];
  /** Whether the user shares any circle with anyone. */
  hasPeople: boolean;
}

export type HolidayReminder =
  | { kind: "self"; holiday: HolidayDef; days: number }
  | { kind: "partner"; holiday: HolidayDef; days: number; partnerName: string }
  | { kind: "others"; holiday: HolidayDef; days: number };

/**
 * At most one holiday reminder per user per day. Couple holidays only go to
 * people in a two-person circle; nothing goes to people with no circles.
 */
export function dueHolidayReminder(holidays: readonly HolidayDef[], today: CalendarDate, ctx: HolidayContext): HolidayReminder | null {
  if (!ctx.hasPeople) return null;
  for (const holiday of holidays) {
    const couple = holiday.audience === "couple";
    if (couple && ctx.partnerNames.length === 0) continue;
    const days = daysUntilHoliday(holiday, today);
    if (days === HOLIDAY_SELF_DAYS) return { kind: "self", holiday, days };
    if (days === HOLIDAY_OTHERS_DAYS) {
      return couple
        ? { kind: "partner", holiday, days, partnerName: ctx.partnerNames[0]! }
        : { kind: "others", holiday, days };
    }
  }
  return null;
}

/** IANA time zones whose local hour is `hour` right now. */
export function timeZonesAtHour(instant: Date, hour: number): string[] {
  const zones = typeof Intl.supportedValuesOf === "function" ? Intl.supportedValuesOf("timeZone") : ["UTC"];
  return [...zones, "UTC"].filter((zone) => {
    const h = Number(new Intl.DateTimeFormat("en-US", { timeZone: zone, hour: "numeric", hourCycle: "h23" }).format(instant));
    return h === hour;
  });
}

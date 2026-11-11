import { describe, expect, it } from "vitest";
import { HOLIDAYS, daysUntilHoliday, dueHolidayReminder, enabledHolidays, timeZonesAtHour } from "../../src/lib/holidays";
import { daysUntilNext, dueReminderOffset, localNow, occurrenceIn } from "../../src/lib/dates";
import { normalizeInviteCode, generateInviteCode } from "../../src/lib/invites";
import { occasionName, render, resolveLocale } from "../../src/lib/messages";
import { isPrivateAddress, parseProductMetadata } from "../../src/lib/unfurl";
import { changedMembers, computeConnections, computeViewerIds, sameIds } from "../../src/lib/visibility";

describe("visibility", () => {
  const circles = new Map([
    ["family", { id: "family", memberIds: ["maya", "dana", "noa"] }],
    ["us", { id: "us", memberIds: ["maya", "dana"] }],
    ["strangers", { id: "strangers", memberIds: ["eve", "mallory"] }],
  ]);

  it("unions members of shared circles and never includes the owner", () => {
    expect(computeViewerIds("maya", ["family", "us"], circles)).toEqual(["dana", "noa"]);
  });

  it("ignores circles the owner is not part of, and missing circles", () => {
    expect(computeViewerIds("maya", ["strangers", "gone"], circles)).toEqual([]);
  });

  it("shares with every circle of the owner for the \"*\" entry", () => {
    const owned = new Map([...circles].filter(([, c]) => c.memberIds.includes("maya")));
    expect(computeViewerIds("maya", ["*"], owned)).toEqual(["dana", "noa"]);
  });

  it("computes connections across circles", () => {
    expect(computeConnections("dana", [...circles.values()])).toEqual(["maya", "noa"]);
  });

  it("compares and diffs id lists", () => {
    expect(sameIds(["a", "b"], ["b", "a"])).toBe(true);
    expect(sameIds(["a"], undefined)).toBe(false);
    expect(changedMembers(["a", "b"], ["b", "c"])).toEqual(["a", "c"]);
  });
});

describe("dates", () => {
  it("counts days to the next yearly occurrence, wrapping the year", () => {
    expect(daysUntilNext({ month: 11, day: 23, recurrence: "yearly" }, { year: 2026, month: 11, day: 11 })).toBe(12);
    expect(daysUntilNext({ month: 11, day: 11, recurrence: "yearly" }, { year: 2026, month: 11, day: 11 })).toBe(0);
    expect(daysUntilNext({ month: 1, day: 1, recurrence: "yearly" }, { year: 2026, month: 12, day: 31 })).toBe(1);
  });

  it("celebrates 29 February on the 28th in common years", () => {
    expect(occurrenceIn(2027, 2, 29)).toEqual({ year: 2027, month: 2, day: 28 });
    expect(occurrenceIn(2028, 2, 29)).toEqual({ year: 2028, month: 2, day: 29 });
  });

  it("handles one-off occasions", () => {
    expect(daysUntilNext({ month: 12, day: 1, year: 2026, recurrence: "once" }, { year: 2026, month: 11, day: 30 })).toBe(1);
    expect(daysUntilNext({ month: 1, day: 1, year: 2026, recurrence: "once" }, { year: 2026, month: 11, day: 30 })).toBeNull();
  });

  it("reads local time in any zone and falls back to UTC", () => {
    const instant = new Date("2026-11-11T22:30:00Z");
    expect(localNow(instant, "Asia/Jerusalem")).toMatchObject({ year: 2026, month: 11, day: 12, hour: 0 });
    expect(localNow(instant, "Not/AZone")).toMatchObject({ day: 11, hour: 22 });
  });

  it("picks the due reminder offset per audience", () => {
    expect(dueReminderOffset(14, "self")).toBe(14);
    expect(dueReminderOffset(14, "others")).toBeNull();
    expect(dueReminderOffset(0, "others")).toBe(0);
    expect(dueReminderOffset(5, "others", [5])).toBe(5);
    expect(dueReminderOffset(null, "others")).toBeNull();
  });
});

describe("invites", () => {
  it("generates codes that survive normalization", () => {
    for (let i = 0; i < 50; i++) {
      const code = generateInviteCode();
      expect(normalizeInviteCode(code.toLowerCase().replace(/(.{4})/, "$1-"))).toBe(code);
    }
  });

  it("rejects malformed codes", () => {
    expect(normalizeInviteCode("short")).toBeNull();
    expect(normalizeInviteCode("ABCDEF0O")).toBeNull(); // ambiguous characters are never issued
    expect(normalizeInviteCode(42)).toBeNull();
  });
});

describe("messages", () => {
  it("resolves device locales", () => {
    expect(resolveLocale("he-IL")).toBe("he");
    expect(resolveLocale("iw")).toBe("he");
    expect(resolveLocale("ar_EG")).toBe("ar");
    expect(resolveLocale("fr")).toBe("en");
  });

  it("renders every template in every language without leftover placeholders", () => {
    for (const locale of ["en", "he", "ar", "ru"] as const) {
      for (const key of ["selfUpcoming", "othersUpcoming", "othersToday", "claimedWishChanged", "claimedWishRemoved"] as const) {
        const message = render(key, locale, { name: "Maya", days: 7, occasion: occasionName("birthday", locale), wish: "Headphones" });
        expect(message.title + message.body).not.toMatch(/[{}]/);
      }
    }
    expect(render("othersUpcoming", "en", { name: "Maya", days: 7, occasion: "birthday" }).title).toBe("Maya's birthday is in 7 days");
    expect(render("othersUpcoming", "he", { name: "מאיה", days: 7, occasion: occasionName("birthday", "he") }).title).toBe(
      "יום ההולדת של מאיה בעוד 7 ימים",
    );
  });
});

describe("unfurl", () => {
  it("prefers JSON-LD product data", () => {
    const html = `<html><head>
      <title>Shop | Headphones</title>
      <meta property="og:site_name" content="Soundhouse">
      <script type="application/ld+json">{"@context":"https://schema.org","@graph":[{"@type":"Product","name":"Noise-cancelling headphones","image":["/img/a.jpg"],"offers":{"@type":"Offer","price":"349.00","priceCurrency":"usd"}}]}</script>
    </head></html>`;
    expect(parseProductMetadata(html, "https://shop.example/p/1")).toEqual({
      url: "https://shop.example/p/1",
      title: "Noise-cancelling headphones",
      imageUrls: ["https://shop.example/img/a.jpg"],
      siteName: "Soundhouse",
      price: { amount: 349, currency: "USD" },
    });
  });

  it("falls back to Open Graph tags and European number formats", () => {
    const html = `<meta property="og:title" content="  Monstera   plant ">
      <meta property="og:image" content="https://cdn.example/m.jpg">
      <meta property="product:price:amount" content="1.299,50">
      <meta property="product:price:currency" content="EUR">`;
    const result = parseProductMetadata(html, "https://www.plants.example/x");
    expect(result.title).toBe("Monstera plant");
    expect(result.price).toEqual({ amount: 1299.5, currency: "EUR" });
    expect(result.siteName).toBe("plants.example");
  });

  it("blocks private and internal addresses", () => {
    for (const ip of ["127.0.0.1", "10.1.2.3", "172.20.0.1", "192.168.1.1", "169.254.169.254", "::1", "fd00::1", "::ffff:10.0.0.1"]) {
      expect(isPrivateAddress(ip)).toBe(true);
    }
    for (const ip of ["8.8.8.8", "151.101.1.69", "2606:4700::1111"]) {
      expect(isPrivateAddress(ip)).toBe(false);
    }
  });
});

describe("holidays", () => {
  const byId = (id: string) => HOLIDAYS.find((h) => h.id === id)!;

  it("has correct dates for lunar and rule-based holidays", () => {
    expect(byId("tu_bav").dates).toContain("2026-07-29");
    expect(byId("hanukkah").dates).toContain("2026-12-05");
    expect(byId("rosh_hashanah").dates).toContain("2026-09-12");
    expect(byId("eid_al_fitr").dates).toContain("2026-03-20");
    expect(byId("eid_al_adha").dates).toContain("2026-05-27");
    expect(byId("mothers_day").dates).toContain("2026-05-10");
    expect(byId("mothers_day_ru").dates).toContain("2026-11-29");
  });

  it("uses language defaults until the user chooses", () => {
    expect(enabledHolidays(undefined, "he-IL").map((h) => h.id)).toEqual(expect.arrayContaining(["tu_bav", "hanukkah", "valentines"]));
    expect(enabledHolidays(undefined, "ru").map((h) => h.id)).toEqual(expect.arrayContaining(["womens_day", "new_year"]));
    expect(enabledHolidays(["christmas"], "he").map((h) => h.id)).toEqual(["christmas"]);
    expect(enabledHolidays([], "he")).toEqual([]);
  });

  it("counts days to the next occurrence", () => {
    expect(daysUntilHoliday(byId("valentines"), { year: 2027, month: 2, day: 7 })).toBe(7);
  });

  it("sends couple holidays only to people with a partner, naming them", () => {
    const valentines = [byId("valentines")];
    const weekBefore = { year: 2027, month: 2, day: 7 };
    expect(dueHolidayReminder(valentines, weekBefore, { partnerNames: [], hasPeople: true })).toBeNull();
    expect(dueHolidayReminder(valentines, weekBefore, { partnerNames: ["Maya"], hasPeople: true })).toMatchObject({ kind: "partner", partnerName: "Maya", days: 7 });
  });

  it("nudges you to add wishes three weeks before", () => {
    const due = dueHolidayReminder([byId("hanukkah")], { year: 2026, month: 11, day: 14 }, { partnerNames: [], hasPeople: true });
    expect(due).toMatchObject({ kind: "self", days: 21 });
    expect(dueHolidayReminder([byId("hanukkah")], { year: 2026, month: 11, day: 14 }, { partnerNames: [], hasPeople: false })).toBeNull();
  });

  it("finds time zones at a given local hour", () => {
    const zones = timeZonesAtHour(new Date("2026-11-11T08:00:00Z"), 10);
    expect(zones).toContain("Asia/Jerusalem");
    expect(zones).not.toContain("America/New_York");
  });

  it("renders holiday notifications in every language", () => {
    for (const locale of ["en", "he", "ar", "ru"] as const) {
      for (const key of ["holidaySelf", "holidayOthers", "holidayPartner"] as const) {
        const m = render(key, locale, { holiday: byId("tu_bav").name[locale], days: 7, name: "Maya" });
        expect(m.title + m.body).not.toMatch(/[{}]/);
      }
    }
    expect(render("holidayPartner", "he", { holiday: byId("tu_bav").name.he, days: 7, name: "מאיה" }).title).toBe("ט״ו באב בעוד 7 ימים");
  });
});

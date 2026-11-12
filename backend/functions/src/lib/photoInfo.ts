import * as z from "zod/v4";

/**
 * What we try to read from a photo of something someone wants: a product shot,
 * a price tag, a shop window, a screenshot of a store app or a receipt.
 * Every field is optional in practice (null when not visible), so the app only
 * fills what the model is reasonably sure about.
 */
export const PhotoInfoSchema = z.object({
  isProduct: z.boolean().describe("True when the photo shows a specific thing someone could want or buy."),
  title: z.string().nullable().describe("Short product name as a shopper would write it, e.g. 'Sony WH-1000XM6 headphones'."),
  brand: z.string().nullable(),
  model: z.string().nullable().describe("Model name or number when visible or clearly identifiable."),
  category: z.string().nullable().describe("One or two words, e.g. 'headphones', 'running shoes'."),
  color: z.string().nullable(),
  size: z.string().nullable(),
  store: z.string().nullable().describe("Retailer the photo is from: a logo, bag, price tag, receipt or store app screenshot. Not the brand."),
  storeDomain: z.string().nullable().describe("The retailer's web domain only when the store is certain, e.g. 'zara.com'."),
  priceAmount: z.number().nullable().describe("Price shown in the photo, as a number. Null when no price is visible."),
  priceCurrency: z.string().nullable().describe("ISO 4217 code of the visible price, e.g. 'ILS', 'USD'."),
  url: z.string().nullable().describe("A product URL only when it is fully readable in the photo (e.g. a browser screenshot)."),
  barcode: z.string().nullable().describe("Digits of a visible EAN/UPC barcode."),
  note: z.string().nullable().describe("One short line with details that help a gift buyer pick the right item (variant, material, size)."),
  searchQuery: z.string().nullable().describe("The best web search to find this exact product online."),
  confidence: z.enum(["low", "medium", "high"]).describe("How sure you are about the product identity."),
});

export type PhotoInfo = z.infer<typeof PhotoInfoSchema>;

const LANGUAGES: Record<string, string> = { he: "Hebrew", ar: "Arabic", ru: "Russian", en: "English" };

export function languageName(locale: unknown): string {
  const code = typeof locale === "string" ? locale.slice(0, 2).toLowerCase() : "en";
  return LANGUAGES[code === "iw" ? "he" : code] ?? "English";
}

export const SYSTEM_PROMPT = `You help people build a gift wish list. The user photographed something they would like to receive: a product, a price tag, a shop shelf or window, a screenshot of an online store or app, a catalog page, or a receipt.

Extract what a gift buyer needs to find and buy the same item. Report only what you can see in the photo or identify with confidence; use null for everything else rather than guessing. A brand is not a store: fill "store" only from evidence of where it is sold (store logo, price tag, bag, receipt, store app or website UI). Read prices exactly as printed, without converting currencies. If the photo shows several products, describe the most prominent one. If the photo is not of something someone could want (a selfie, a landscape, a document), set isProduct to false.

Write "title" in the product's own language as printed (brand and model names stay as they are). Write "note" in the user's language.`;

/** Accepts a JPEG/PNG/WebP/HEIC image as base64 and returns its media type, or throws. */
export function sniffImage(base64: string): { mediaType: "image/jpeg" | "image/png" | "image/webp"; bytes: number } {
  const bytes = Math.floor((base64.length * 3) / 4);
  const head = Buffer.from(base64.slice(0, 24), "base64");
  if (head[0] === 0xff && head[1] === 0xd8) return { mediaType: "image/jpeg", bytes };
  if (head[0] === 0x89 && head.toString("ascii", 1, 4) === "PNG") return { mediaType: "image/png", bytes };
  if (head.toString("ascii", 0, 4) === "RIFF" && head.toString("ascii", 8, 12) === "WEBP") return { mediaType: "image/webp", bytes };
  throw new Error("unsupported-image");
}

/** Drops values that are blank or obviously malformed so clients can trust what they get. */
export function cleanPhotoInfo(info: PhotoInfo): PhotoInfo {
  const text = (value: string | null, max: number): string | null => {
    const trimmed = value?.trim();
    return trimmed ? trimmed.slice(0, max) : null;
  };
  const url = text(info.url, 2048);
  const currency = text(info.priceCurrency, 3)?.toUpperCase() ?? null;
  return {
    ...info,
    title: text(info.title, 200),
    brand: text(info.brand, 80),
    model: text(info.model, 80),
    category: text(info.category, 60),
    color: text(info.color, 40),
    size: text(info.size, 40),
    store: text(info.store, 80),
    storeDomain: text(info.storeDomain, 100)?.toLowerCase().replace(/^https?:\/\//, "").replace(/\/.*$/, "") ?? null,
    priceAmount: typeof info.priceAmount === "number" && Number.isFinite(info.priceAmount) && info.priceAmount > 0 ? info.priceAmount : null,
    priceCurrency: currency && /^[A-Z]{3}$/.test(currency) ? currency : null,
    url: url && /^https?:\/\/[^\s]+\.[^\s]+/.test(url) ? url : null,
    barcode: text(info.barcode, 20)?.replace(/\D/g, "") || null,
    note: text(info.note, 300),
    searchQuery: text(info.searchQuery, 200),
  };
}

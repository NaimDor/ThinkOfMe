import * as z from "zod/v4";

/** What the AI fallback reads from a store page when its metadata is incomplete. */
export const LinkInfoSchema = z.object({
  isProduct: z.boolean().describe("True when the page is about one specific product someone could buy."),
  title: z.string().nullable().describe("Short product name as a shopper would write it."),
  brand: z.string().nullable(),
  store: z.string().nullable().describe("The shop selling it (the website's store name), not the brand."),
  priceAmount: z.number().nullable().describe("Current selling price shown on the page, as a number."),
  priceCurrency: z.string().nullable().describe("ISO 4217 code of that price, e.g. 'ILS', 'USD'."),
  note: z.string().nullable().describe("One short line with details a gift buyer needs (variant, size, color)."),
  imageIndex: z.number().int().nullable().describe("Index of the main product photo in the numbered image list, or null."),
});

export type LinkInfo = z.infer<typeof LinkInfoSchema>;

export const LINK_SYSTEM_PROMPT = `You help people build a gift wish list from links to online stores. You get the text of a product page and a numbered list of image URLs from it.

Extract what a gift buyer needs. Report only what the page states; use null otherwise. Use the current selling price (after any discount), exactly as shown, without converting currencies. "store" is the shop selling the product, not the brand. For imageIndex pick the main photo of the product itself; skip logos, banners, icons, payment badges and other products. Write "note" in the user's language; keep the title in the product's own language.`;

/** The user message: page address, text and numbered image candidates. */
export function linkPrompt(url: string, text: string, images: string[], language: string): string {
  const list = images.map((image, index) => `${index}: ${image}`).join("\n") || "(none)";
  return `Page: ${url}\nThe user's language is ${language}.\n\nImages:\n${list}\n\nPage text:\n${text}`;
}

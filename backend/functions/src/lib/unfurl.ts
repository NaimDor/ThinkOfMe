import { load } from "cheerio";
import { lookup } from "node:dns/promises";
import { isIP } from "node:net";

/** What we can learn about a product page. Every field is optional. */
export interface LinkMetadata {
  url: string;
  title?: string;
  imageUrls: string[];
  siteName?: string;
  price?: { amount: number; currency: string };
}

const MAX_TITLE = 140;
const MAX_IMAGES = 6;

function clean(text: string | undefined | null, max: number): string | undefined {
  if (!text) return undefined;
  const collapsed = text.replace(/\s+/g, " ").trim();
  if (!collapsed) return undefined;
  return collapsed.length > max ? `${collapsed.slice(0, max - 1).trimEnd()}…` : collapsed;
}

function absolute(candidate: string | undefined, base: string): string | undefined {
  if (!candidate) return undefined;
  try {
    const resolved = new URL(candidate.trim(), base);
    return resolved.protocol === "https:" || resolved.protocol === "http:" ? resolved.toString() : undefined;
  } catch {
    return undefined;
  }
}

function parsePrice(amount: unknown, currency: unknown): { amount: number; currency: string } | undefined {
  const raw = typeof amount === "number" ? String(amount) : typeof amount === "string" ? amount : undefined;
  if (!raw || typeof currency !== "string" || !/^[A-Za-z]{3}$/.test(currency.trim())) return undefined;
  // Accept "1,299.00", "1.299,00" and "1299".
  let normalized = raw.replace(/[^\d.,]/g, "");
  if (/,\d{1,2}$/.test(normalized) && normalized.includes(".")) {
    normalized = normalized.replace(/\./g, "").replace(",", ".");
  } else if (/,\d{1,2}$/.test(normalized)) {
    normalized = normalized.replace(",", ".");
  } else {
    normalized = normalized.replace(/,/g, "");
  }
  const value = Number(normalized);
  if (!Number.isFinite(value) || value <= 0 || value > 10_000_000) return undefined;
  return { amount: Math.round(value * 100) / 100, currency: currency.trim().toUpperCase() };
}

type JsonObject = Record<string, unknown>;

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : value == null ? [] : [value];
}

function findProduct(node: unknown): JsonObject | undefined {
  for (const item of asArray(node)) {
    if (!item || typeof item !== "object") continue;
    const obj = item as JsonObject;
    const types = asArray(obj["@type"]).map(String);
    if (types.some((t) => t.toLowerCase() === "product")) return obj;
    const nested = findProduct(obj["@graph"]);
    if (nested) return nested;
  }
  return undefined;
}

/** Extract product metadata from an HTML document. Pure and synchronous. */
export function parseProductMetadata(html: string, pageUrl: string): LinkMetadata {
  const $ = load(html);
  const meta = (key: string): string | undefined =>
    $(`meta[property="${key}"]`).attr("content") ?? $(`meta[name="${key}"]`).attr("content") ?? undefined;

  let product: JsonObject | undefined;
  $('script[type="application/ld+json"]').each((_, element) => {
    if (product) return;
    try {
      product = findProduct(JSON.parse($(element).contents().text()));
    } catch {
      // Ignore malformed JSON-LD blocks.
    }
  });

  const images = new Set<string>();
  const addImage = (candidate: unknown): void => {
    const value =
      typeof candidate === "string"
        ? candidate
        : candidate && typeof candidate === "object"
          ? String((candidate as JsonObject).url ?? "")
          : undefined;
    const resolved = absolute(value, pageUrl);
    if (resolved && images.size < MAX_IMAGES) images.add(resolved);
  };
  for (const img of asArray(product?.image)) addImage(img);
  $('meta[property="og:image"], meta[property="og:image:secure_url"], meta[name="twitter:image"]').each((_, el) =>
    addImage($(el).attr("content")),
  );

  let price: LinkMetadata["price"];
  for (const offer of asArray(product?.offers)) {
    if (!offer || typeof offer !== "object") continue;
    const o = offer as JsonObject;
    price = parsePrice(o.price ?? o.lowPrice, o.priceCurrency);
    if (price) break;
  }
  price ??= parsePrice(meta("product:price:amount") ?? meta("og:price:amount"), meta("product:price:currency") ?? meta("og:price:currency"));

  const title = clean(
    (typeof product?.name === "string" ? product.name : undefined) ?? meta("og:title") ?? meta("twitter:title") ?? $("title").first().text(),
    MAX_TITLE,
  );
  const siteName = clean(meta("og:site_name") ?? new URL(pageUrl).hostname.replace(/^www\./, ""), 80);

  return { url: pageUrl, title, imageUrls: [...images], siteName, price };
}

// ---------- SSRF protection ----------

function ipv4ToInt(ip: string): number {
  return ip.split(".").reduce((acc, octet) => (acc << 8) + Number(octet), 0) >>> 0;
}

function inRange(ip: string, cidr: string): boolean {
  const [base, bits] = cidr.split("/");
  const mask = bits === "0" ? 0 : (~0 << (32 - Number(bits))) >>> 0;
  return (ipv4ToInt(ip) & mask) === (ipv4ToInt(base ?? "0.0.0.0") & mask);
}

const BLOCKED_V4 = [
  "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16", "172.16.0.0/12",
  "192.0.0.0/24", "192.168.0.0/16", "198.18.0.0/15", "224.0.0.0/4", "240.0.0.0/4",
];

/** True when an IP literal points at a private, loopback or otherwise internal address. */
export function isPrivateAddress(ip: string): boolean {
  const family = isIP(ip);
  if (family === 4) return BLOCKED_V4.some((range) => inRange(ip, range));
  if (family === 6) {
    const lower = ip.toLowerCase();
    if (lower === "::" || lower === "::1") return true;
    const mapped = lower.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/);
    if (mapped?.[1]) return isPrivateAddress(mapped[1]);
    return /^(fc|fd|fe8|fe9|fea|feb|ff)/.test(lower);
  }
  return true;
}

export class UnfurlError extends Error {}

/** Throws unless the URL is http(s) on a default port and resolves only to public addresses. */
export async function assertPublicUrl(raw: string): Promise<URL> {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    throw new UnfurlError("invalid-url");
  }
  if (url.protocol !== "https:" && url.protocol !== "http:") throw new UnfurlError("unsupported-protocol");
  if (url.port && url.port !== "80" && url.port !== "443") throw new UnfurlError("unsupported-port");
  if (url.username || url.password) throw new UnfurlError("credentials-in-url");
  const host = url.hostname.replace(/^\[|\]$/g, "");
  const addresses = isIP(host) ? [{ address: host }] : await lookup(host, { all: true, verbatim: true });
  if (addresses.length === 0 || addresses.some((entry) => isPrivateAddress(entry.address))) {
    throw new UnfurlError("blocked-host");
  }
  return url;
}

const MAX_BYTES = 1_500_000;
const USER_AGENT = "Mozilla/5.0 (compatible; ThinkOfMeLinkPreview/1.0)";

/** Fetch a page safely (validated redirects, size and time limits) and parse it. */
export async function unfurl(raw: string): Promise<LinkMetadata> {
  let current = await assertPublicUrl(raw);
  for (let hop = 0; hop < 4; hop++) {
    const response = await fetch(current, {
      redirect: "manual",
      signal: AbortSignal.timeout(8000),
      headers: { "user-agent": USER_AGENT, accept: "text/html,application/xhtml+xml", "accept-language": "en,he;q=0.8,ar;q=0.6,ru;q=0.6" },
    });
    if (response.status >= 300 && response.status < 400) {
      const location = response.headers.get("location");
      if (!location) throw new UnfurlError("bad-redirect");
      current = await assertPublicUrl(new URL(location, current).toString());
      continue;
    }
    if (!response.ok) throw new UnfurlError(`http-${response.status}`);
    const type = response.headers.get("content-type") ?? "";
    if (!type.includes("html")) throw new UnfurlError("not-html");
    const html = await readLimited(response, MAX_BYTES);
    return parseProductMetadata(html, current.toString());
  }
  throw new UnfurlError("too-many-redirects");
}

async function readLimited(response: Response, maxBytes: number): Promise<string> {
  if (!response.body) return "";
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done || !value) break;
    total += value.byteLength;
    chunks.push(value);
    if (total >= maxBytes) {
      await reader.cancel();
      break;
    }
  }
  return Buffer.concat(chunks.map((chunk) => Buffer.from(chunk))).toString("utf8");
}

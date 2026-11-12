import { describe, expect, it } from "vitest";
import { cleanPhotoInfo, languageName, PhotoInfo, sniffImage } from "../../src/lib/photoInfo";

const base: PhotoInfo = {
  isProduct: true, title: null, brand: null, model: null, category: null, color: null, size: null,
  store: null, storeDomain: null, priceAmount: null, priceCurrency: null, url: null, barcode: null,
  note: null, searchQuery: null, confidence: "high",
};

describe("sniffImage", () => {
  it("recognises JPEG, PNG and WebP", () => {
    expect(sniffImage(Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0, 0, 0, 0]).toString("base64")).mediaType).toBe("image/jpeg");
    expect(sniffImage(Buffer.from("\x89PNG\r\n\x1a\n0000", "latin1").toString("base64")).mediaType).toBe("image/png");
    expect(sniffImage(Buffer.from("RIFF0000WEBPVP8 ", "latin1").toString("base64")).mediaType).toBe("image/webp");
  });
  it("rejects anything else", () => {
    expect(() => sniffImage(Buffer.from("<html>hello</html>").toString("base64"))).toThrow();
  });
});

describe("cleanPhotoInfo", () => {
  it("trims, normalises and drops malformed values", () => {
    const out = cleanPhotoInfo({
      ...base, title: "  Sony WH-1000XM6  ", store: " ", storeDomain: "https://www.ksp.co.il/web/item",
      priceAmount: -3, priceCurrency: "ils", url: "not a url", barcode: "4 901780 123456",
    });
    expect(out.title).toBe("Sony WH-1000XM6");
    expect(out.store).toBeNull();
    expect(out.storeDomain).toBe("www.ksp.co.il");
    expect(out.priceAmount).toBeNull();
    expect(out.priceCurrency).toBe("ILS");
    expect(out.url).toBeNull();
    expect(out.barcode).toBe("4901780123456");
  });
  it("keeps good values", () => {
    const out = cleanPhotoInfo({ ...base, priceAmount: 349.9, priceCurrency: "USD", url: "https://example.com/p/1" });
    expect(out.priceAmount).toBe(349.9);
    expect(out.url).toBe("https://example.com/p/1");
  });
});

describe("languageName", () => {
  it("maps app locales to language names", () => {
    expect(languageName("he-IL")).toBe("Hebrew");
    expect(languageName("iw")).toBe("Hebrew");
    expect(languageName("ar")).toBe("Arabic");
    expect(languageName("ru_RU")).toBe("Russian");
    expect(languageName(undefined)).toBe("English");
  });
});

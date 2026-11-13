import { describe, expect, it } from "vitest";
import { pageDigest } from "../../src/lib/unfurl";

const html = `<!doctype html><html><head><title>Aura X2 headphones | Soundhouse</title>
<meta name="description" content="Wireless noise-cancelling headphones">
<script>var tracking = "do not read me";</script><style>.x{color:red}</style></head>
<body><nav>Home Shop Cart</nav>
<main><h1>Aura X2</h1><p>Price: ₪349.90</p>
<img src="/img/logo.svg" alt="logo">
<img src="/img/pixel.gif" width="1">
<div class="product-gallery"><img src="/media/aura-x2-black.jpg" alt="Aura X2 black" width="1000"></div>
<img src="https://cdn.example.com/banner-sale.jpg" width="300">
</main><footer>Terms</footer></body></html>`;

describe("pageDigest", () => {
  const digest = pageDigest(html, "https://shop.example.com/p/aura-x2");

  it("keeps the readable text and drops scripts, styles and navigation", () => {
    expect(digest.text).toContain("Aura X2 headphones | Soundhouse");
    expect(digest.text).toContain("₪349.90");
    expect(digest.text).not.toContain("do not read me");
    expect(digest.text).not.toContain("color:red");
    expect(digest.text).not.toContain("Home Shop Cart");
  });

  it("ranks the product photo first and skips logos and tracking pixels", () => {
    expect(digest.images[0]).toBe("https://shop.example.com/media/aura-x2-black.jpg");
    expect(digest.images.some((url) => url.includes("logo"))).toBe(false);
    expect(digest.images.some((url) => url.includes("pixel"))).toBe(false);
  });

  it("puts images already found in the page metadata first", () => {
    const withKnown = pageDigest(html, "https://shop.example.com/p/aura-x2", ["https://cdn.example.com/og.jpg"]);
    expect(withKnown.images[0]).toBe("https://cdn.example.com/og.jpg");
  });
});

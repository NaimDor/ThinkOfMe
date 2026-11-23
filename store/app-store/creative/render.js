// Renders the App Store creative assets (product page header and search results) and the
// Google Play feature graphic (1024 x 500, <locale>-feature.png) for every
// language into store/app-store/creative/out/ (git-ignored). Usage: node render.js
// Needs Playwright with Chromium, and Pillow (python3) to flatten to RGB: Apple rejects alpha.
const { chromium } = require('playwright');
const path = require('path');
const { execFileSync } = require('child_process');
const fs = require('fs');
const langs = { en: 'en-US', he: 'he', ar: 'ar-SA', ru: 'ru' };
(async () => {
  const out = path.join(__dirname, 'out');
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch();
  for (const kind of ['header', 'search', 'feature']) for (const [lang, dir] of Object.entries(langs)) {
    const size = { header: [3840, 1646], search: [3840, 2560], feature: [1024, 500] }[kind];
    const page = await browser.newPage({ viewport: { width: size[0], height: size[1] } });
    await page.goto('file://' + path.join(__dirname, 'art.html') + `?kind=${kind}&lang=${lang}`);
    await page.evaluate(() => window.ready);
    const file = path.join(out, `${dir}-${kind}.png`);
    await page.screenshot({ path: file });
    execFileSync('python3', ['-c', 'import sys;from PIL import Image;Image.open(sys.argv[1]).convert("RGB").save(sys.argv[1],optimize=True)', file]);
    await page.close();
    console.log(file);
  }
  await browser.close();
})();

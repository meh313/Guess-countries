// Renders the bundled flag images (app/src/main/res/drawable-nodpi/flag_xx.webp) from flag-icons.
//
//   npm pack flag-icons@7.5.0 && tar xzf flag-icons-7.5.0.tgz     # creates ./package
//   npm i playwright-core && pip install pillow
//   CHROME=/path/to/chrome node /path/to/repo/tools/render-flags.mjs   # writes ./out/<code>.png
//   python3 tools/to-webp.py                                      # lossless WebP into the app
//
// The 4x3 SVGs are rendered at their own shape and never stretched, so circles stay round. The page is
// transparent: where two shapes meet the edge pixel is partly transparent, and to-webp.py keeps its colour
// (dropping the alpha) instead of blending it with a background, which would draw a pale hairline.
import fs from 'fs';
import { createRequire } from 'module';

// Resolve playwright-core from the directory the script is run in (where `npm i` put it).
const { chromium } = createRequire(process.cwd() + '/')('playwright-core');

const codes = 'fr de it gb es gr se no ua us ca mx br ar co cl jp cn in kr vn th sa eg ke za ng ma tz au nz fj aq'.split(' ');
const W = 600, H = 450, SCALE = 1;
const browser = await chromium.launch({ executablePath: process.env.CHROME, args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: SCALE });
fs.mkdirSync('out', { recursive: true });
for (const c of codes) {
  const svg = fs.readFileSync(`package/flags/4x3/${c}.svg`, 'utf8')
    .replace(/<svg([^>]*)>/, (m, attrs) =>
      `<svg${attrs.replace(/\s(width|height)="[^"]*"/g, '')} width="${W}" height="${H}">`);
  await page.setContent(`<html><body style="margin:0">${svg}</body></html>`);
  await page.screenshot({ path: `out/${c}.png`, clip: { x: 0, y: 0, width: W, height: H }, omitBackground: true });
}
await browser.close();

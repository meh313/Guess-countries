// Renders flag SVGs to PNG with Chromium, unstretched, for to-webp.py.
//
//   cd /tmp/flagwork            # holds package/ (flag-icons), cfi/ (country-flag-icons), node_modules/
//   CHROME=/path/to/chrome node <repo>/tools/render-flags.mjs [--force] [--source cfi] \
//       [--seed <repo>/content/seed/europe.json] [xk tw ...]
//
// Codes come from the arguments, else from the seed file, else from every catalog file. flag-icons
// (4:3, the app's artwork) renders into out/<code>.png at 600x450; --source cfi renders the independent
// country-flag-icons drawing (3:2) into ref/<code>.png at 600x400 for side-by-side comparison only.
// Existing files are skipped unless --force.
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const { chromium } = createRequire(process.cwd() + '/')('playwright-core');
const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const args = process.argv.slice(2);
const flag = (name) => { const i = args.indexOf(name); if (i < 0) return null; const v = args[i + 1]; args.splice(i, 2); return v; };
const force = args.includes('--force'); if (force) args.splice(args.indexOf('--force'), 1);
const source = flag('--source') || 'flag-icons';
const seed = flag('--seed');

let codes = args.map(c => c.toLowerCase());
if (!codes.length && seed) codes = JSON.parse(fs.readFileSync(seed, 'utf8')).entries.map(e => e.code.toLowerCase());
if (!codes.length) {
  const dir = path.join(repo, 'app/src/main/java/com/example/data/model');
  for (const f of fs.readdirSync(dir).filter(f => f.endsWith('Catalog.kt'))) {
    for (const m of fs.readFileSync(path.join(dir, f), 'utf8').matchAll(/code = "([A-Z]{2})"/g)) codes.push(m[1].toLowerCase());
  }
}

const cfi = source === 'cfi';
const outDir = cfi ? 'ref' : 'out';
const W = 600, H = cfi ? 400 : 450;
fs.mkdirSync(outDir, { recursive: true });
const browser = await chromium.launch({ executablePath: process.env.CHROME, args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
let done = 0, skipped = 0;
for (const c of codes) {
  const target = `${outDir}/${c}.png`;
  if (!force && fs.existsSync(target)) { skipped++; continue; }
  const file = cfi ? `cfi/package/3x2/${c.toUpperCase()}.svg` : `package/flags/4x3/${c}.svg`;
  const svg = fs.readFileSync(file, 'utf8')
    .replace(/<svg([^>]*)>/, (m, attrs) => `<svg${attrs.replace(/\s(width|height)="[^"]*"/g, '')} width="${W}" height="${H}">`);
  // Transparent page: where two shapes meet, the edge pixel keeps its colour (to-webp.py drops the alpha)
  // instead of being blended with a background, which would draw a pale hairline.
  await page.setContent(`<html><body style="margin:0">${svg}</body></html>`);
  await page.screenshot({ path: target, clip: { x: 0, y: 0, width: W, height: H }, omitBackground: true });
  done++;
}
await browser.close();
console.log(`${done} rendered, ${skipped} already present, in ${outDir}/`);

#!/usr/bin/env node
// Editorial Dusk & Dawn: builds theme files from design/tokens.json.
//
//   node design/build-tokens.mjs --css <out.css> [--fonts <dir>]
//   node design/build-tokens.mjs --kotlin <out.kt> --package <pkg>
//   node design/build-tokens.mjs --check
//
// Every run checks contrast first and exits non-zero if a pair in
// tokens.json `contrast.checks` falls below its minimum in either theme.
// No dependencies, so the same file runs in every repo.

import { readFileSync, writeFileSync, copyFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

const here = dirname(fileURLToPath(import.meta.url));
const tokens = JSON.parse(readFileSync(join(here, 'tokens.json'), 'utf8'));
const THEMES = Object.keys(tokens.themes);
const HEADER = 'Generated from design/tokens.json by design/build-tokens.mjs. Do not edit.';

const args = process.argv.slice(2);
const opt = (name) => {
  const i = args.indexOf(name);
  return i === -1 ? undefined : args[i + 1];
};

// ---------- colour maths ----------

function parse(value) {
  const v = value.trim();
  if (v.startsWith('#')) {
    let h = v.slice(1);
    if (h.length === 3 || h.length === 4) h = [...h].map((c) => c + c).join('');
    const n = (i) => parseInt(h.slice(i, i + 2), 16);
    return { r: n(0), g: n(2), b: n(4), a: h.length === 8 ? n(6) / 255 : 1 };
  }
  const m = /^rgba?\(([^)]+)\)$/.exec(v);
  if (!m) throw new Error(`Unsupported colour: ${value}`);
  const [r, g, b, a = 1] = m[1].split(',').map((s) => parseFloat(s));
  return { r, g, b, a };
}

const over = (top, under) => ({
  r: top.r * top.a + under.r * (1 - top.a),
  g: top.g * top.a + under.g * (1 - top.a),
  b: top.b * top.a + under.b * (1 - top.a),
  a: 1,
});

function luminance({ r, g, b }) {
  const lin = (c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

const ratio = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

// ---------- contrast check ----------

function check() {
  const { surfaces, checks } = tokens.contrast;
  const page = ['bg-grad-top', 'bg-grad-mid', 'bg-grad-bot'];
  const failures = [];
  let count = 0;
  for (const theme of THEMES) {
    const col = (name) => {
      const t = tokens.color[name];
      if (!t) throw new Error(`contrast check names unknown token "${name}"`);
      return parse(t[theme]);
    };
    // A translucent background is judged over every page stop it can sit on.
    const grounds = (name) => {
      const c = col(name);
      return c.a < 1 ? page.map((p) => ({ name: `${name} over ${p}`, c: over(c, col(p)) })) : [{ name, c }];
    };
    for (const { fg, bg, min } of checks) {
      const bgs = bg === 'surfaces' ? surfaces : bg;
      for (const f of fg) {
        for (const b of bgs) {
          for (const g of grounds(b)) {
            const fc = col(f);
            const r = ratio(fc.a < 1 ? over(fc, g.c) : fc, g.c);
            count++;
            if (r < min) failures.push(`${theme}: ${f} on ${g.name} is ${r.toFixed(2)}:1, needs ${min}:1`);
          }
        }
      }
    }
  }
  if (failures.length) {
    console.error(`Contrast check failed (${failures.length} of ${count} pairs):\n  ${failures.join('\n  ')}`);
    process.exit(1);
  }
  console.log(`Contrast check passed: ${count} pairs in ${THEMES.length} themes.`);
}

// ---------- CSS ----------

function css(fontUrl) {
  const out = [`/* ${HEADER} */`, ''];
  if (fontUrl !== undefined) {
    for (const f of tokens.fontFaces) {
      out.push(
        '@font-face {',
        `  font-family: "${f.family}";`,
        `  src: url("${fontUrl}${f.file}") format("woff2");`,
        `  font-style: ${f.style};`,
        `  font-weight: ${f.weight};`,
        '  font-display: swap;',
        '}',
        '',
      );
    }
  }
  out.push(':root {');
  for (const [k, v] of Object.entries(tokens.font)) out.push(`  --font-${k}: ${v};`);
  for (const group of ['space', 'radius', 'motion', 'layout']) {
    for (const [k, v] of Object.entries(tokens[group])) out.push(`  --${k}: ${v};`);
  }
  out.push('}', '');
  for (const theme of THEMES) {
    const { selector, colorScheme } = tokens.themes[theme];
    out.push(`${selector} {`, `  color-scheme: ${colorScheme};`);
    for (const group of ['color', 'rgb', 'number']) {
      for (const [k, v] of Object.entries(tokens[group])) out.push(`  --${k}: ${v[theme]};`);
    }
    out.push('}', '');
  }
  return out.join('\n');
}

// ---------- Kotlin (Waymark, until it moves to Svelte) ----------

const camel = (s) => s.replace(/-([a-z0-9])/g, (_, c) => c.toUpperCase());
// ktlint and detekt: a constant is a const val in SCREAMING_SNAKE_CASE.
const screaming = (s) => s.replace(/-/g, '_').toUpperCase();

function argb(value) {
  const { r, g, b, a } = parse(value);
  const hex = (n) => Math.round(n).toString(16).padStart(2, '0').toUpperCase();
  return `0x${hex(a * 255)}${hex(r)}${hex(g)}${hex(b)}`;
}

function kotlin(pkg) {
  const out = [`// ${HEADER}`, `package ${pkg}`, '', 'import androidx.compose.ui.graphics.Color', ''];
  for (const theme of THEMES) {
    const name = theme[0].toUpperCase() + theme.slice(1) + 'Tokens';
    out.push(`internal object ${name} {`);
    for (const [k, v] of Object.entries(tokens.color)) out.push(`    val ${camel(k)} = Color(${argb(v[theme])})`);
    for (const [k, v] of Object.entries(tokens.rgb)) out.push(`    val ${camel(k)} = Color(${argb(`rgb(${v[theme]})`)})`);
    for (const [k, v] of Object.entries(tokens.number)) out.push(`    const val ${screaming(k)} = ${v[theme]}f`);
    out.push('}', '');
  }
  return out.join('\n');
}

// ---------- run ----------

check();

const cssOut = opt('--css');
if (cssOut) {
  const fonts = opt('--fonts');
  writeFileSync(cssOut, css(fonts === undefined ? undefined : './fonts/'));
  if (fonts) {
    mkdirSync(fonts, { recursive: true });
    for (const f of tokens.fontFaces) copyFileSync(join(here, 'fonts', f.file), join(fonts, f.file));
  }
  console.log(`Wrote ${cssOut}`);
}

const ktOut = opt('--kotlin');
if (ktOut) {
  const pkg = opt('--package');
  if (!pkg) throw new Error('--kotlin needs --package');
  writeFileSync(ktOut, kotlin(pkg));
  console.log(`Wrote ${ktOut}`);
}

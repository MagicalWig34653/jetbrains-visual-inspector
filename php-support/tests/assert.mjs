// Assertions for php-support/tests/check.sh. Offsets are computed here, independently of the PHP code:
// the expected offset of a tag is JS String.indexOf (UTF-16 index) of its literal text in the source file
// with line breaks normalized to \n, which is what the IDE Document offset means.
//   node assert.mjs check <fixtureDir> <enabled.html> [outsideFile]
//   node assert.mjs plain <disabled.html>
//   node assert.mjs same <a.html> <b.html>
//   node assert.mjs stripped <enabled.html> <disabled.html>
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

let failures = 0;
const ok = (cond, msg) => { if (!cond) { failures++; console.log(`  FAIL ${msg}`); } };
const read = (f) => readFileSync(f, 'utf8');
const escRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const ATTR = /\sdata-vi-src="[^"]*"/g;

// [file, literal start tag in the source, occurrences in the page]
const MAPPED = [
  ['index.php', '<html lang="en">', 1], ['index.php', '<head>', 1], ['index.php', '<meta charset="utf-8">', 1],
  ['index.php', '<title>', 1], ['index.php', '<body>', 1], ['index.php', '<main id="main">', 1],
  ['index.php', '<h1 id="unicode">', 1],
  ['index.php', '<ul id="loop">', 1], // after a line with non-ASCII and a surrogate pair
  ['index.php', '<li class="loop-item">', 3], ['index.php', '<span class="loop-label">', 3],
  ['index.php', '<ul id="partial-loop">', 1], ['index.php', '<ul id="echoed">', 1],
  ['index.php', '<div id="split" ', 1], // start tag continues in a PHP block
  ['index.php', '<a id="quoted" href="', 1], // quote spans a PHP block, `>` inside a value
  ['index.php', '<DIV ID="MixedCase">', 1], ['index.php', '<p id="short">', 1],
  ['partials/header.php', '<header id="site-header" class="top">', 1], ['partials/header.php', '<nav>', 1],
  ['partials/header.php', '<a href="/">', 1],
  ['partials/footer.php', '<footer id="site-footer">', 1], ['partials/footer.php', '<p>', 1],
  ['partials/card.php', '<li class="card">', 3], ['partials/card.php', '<b class="card-title">', 3],
];
const UNMAPPED = [ // must appear in the page exactly n times and carry no attribute
  ['<li class="echoed-item">', 3], ['<i id="shortecho">', 1], ['<div id="vendor-block">', 1],
  ['<b id="in-comment">', 1],
];

const cmd = process.argv[2];
if (cmd === 'check') {
  const [, , , dir, htmlFile, outsideFile] = process.argv;
  const html = read(htmlFile);
  let expectedTotal = 0;
  for (const [file, needle, count] of MAPPED) {
    const src = read(join(dir, file)).replace(/\r\n?/g, '\n');
    const at = src.indexOf(needle);
    ok(at >= 0 && src.indexOf(needle, at + 1) < 0, `${file}: needle ${needle} must be unique in source`);
    const name = /^<([A-Za-z0-9]+)/.exec(needle)[1];
    const re = new RegExp(`<${name} data-vi-src="([^"]*)"${escRe(needle.slice(1 + name.length))}`, 'g');
    const found = [...html.matchAll(re)].map((m) => m[1]);
    ok(found.length === count, `${needle}: ${found.length} occurrences in page, expected ${count}`);
    ok(found.every((v) => v === `${file}:${at}`), `${needle}: expected ${file}:${at}, got ${[...new Set(found)]}`);
    expectedTotal += count;
  }
  for (const [needle, n] of UNMAPPED) {
    ok(html.split(needle).length - 1 === n, `${needle} must appear ${n} times without attribute`);
  }
  if (outsideFile) ok(html.includes('<section id="outside-block">'), 'outside-root page content missing');
  const total = (html.match(ATTR) || []).length;
  ok(total === expectedTotal, `attribute total ${total}, expected ${expectedTotal} (no extras, none in comment/title text)`);
  ok(!/<title[^>]*>[^<]*data-vi-src/.test(html), 'attribute leaked into title text');
  console.log(`  checked ${MAPPED.length} mapped tags, ${expectedTotal} attributes in page`);
} else if (cmd === 'plain') {
  ok(!read(process.argv[3]).includes('data-vi-src'), 'attribute present although support is disabled');
} else if (cmd === 'same') {
  ok(read(process.argv[3]) === read(process.argv[4]), 'pages differ');
} else if (cmd === 'stripped') {
  ok(read(process.argv[3]).replace(ATTR, '') === read(process.argv[4]), 'rewriting changed more than the attributes');
} else {
  console.log('unknown command'); failures++;
}
process.exit(failures ? 1 : 0);

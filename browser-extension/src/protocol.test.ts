import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import {
  buildAck,
  buildClear,
  buildError,
  buildHello,
  buildHighlight,
  buildNavigate,
  buildUnmappable,
  ERROR_CODES,
  isValidPath,
  LIMITS,
  parseIdeMessage,
  parsePageMessage,
  parseSrcAttribute,
  PROTOCOL_VERSION,
} from "./protocol.ts";

interface Vector {
  name: string;
  direction: "page" | "ide";
  raw: string;
  valid: boolean;
  code?: string;
}
const fixture = JSON.parse(
  readFileSync(new URL("../../test-fixtures/protocol/vectors.json", import.meta.url), "utf8"),
) as { vectors: Vector[] };

test("protocol version is 1", () => {
  assert.equal(PROTOCOL_VERSION, 1);
});

test("shared vectors: file is non-trivial and names are unique", () => {
  assert.ok(fixture.vectors.length > 100);
  assert.equal(new Set(fixture.vectors.map((v) => v.name + v.direction)).size, fixture.vectors.length);
  assert.ok(fixture.vectors.some((v) => v.valid) && fixture.vectors.some((v) => !v.valid));
});

for (const vector of fixture.vectors) {
  test(`vector (${vector.direction}): ${vector.name}`, () => {
    const result = vector.direction === "page" ? parsePageMessage(vector.raw) : parseIdeMessage(vector.raw);
    if (vector.valid) {
      assert.equal(result.ok, true);
    } else {
      assert.deepEqual(result, { ok: false, code: vector.code });
    }
  });
}

test("every documented parse error code is exercised by a vector", () => {
  const used = new Set(fixture.vectors.map((v) => v.code).filter(Boolean));
  for (const code of ["bad_json", "too_large", "bad_version", "unknown_type", "unknown_field", "bad_field", "bad_path"]) {
    assert.ok(used.has(code), code);
  }
});

test("non-string input is rejected without throwing", () => {
  for (const input of [undefined, null, 1, {}, [], Symbol("x"), () => 1, { toString: () => { throw new Error("x"); } }]) {
    assert.deepEqual(parsePageMessage(input), { ok: false, code: "bad_json" });
    assert.deepEqual(parseIdeMessage(input), { ok: false, code: "bad_json" });
  }
});

test("valid result does not pollute Object.prototype", () => {
  parsePageMessage('{"v":1,"type":"hello","__proto__":{"polluted":true}}');
  assert.equal(({} as Record<string, unknown>).polluted, undefined);
});

test("size limit is exactly maxMessageChars", () => {
  const base = '{"v":1,"type":"hello"}';
  assert.equal(parsePageMessage(base + " ".repeat(LIMITS.maxMessageChars - base.length)).ok, true);
  assert.deepEqual(parsePageMessage(base + " ".repeat(LIMITS.maxMessageChars - base.length + 1)), {
    ok: false,
    code: "too_large",
  });
});

test("parsed message equals input (no repair, no defaults)", () => {
  const result = parsePageMessage('{"v":1,"type":"navigate","path":"a/b.html","offset":7,"index":0,"count":2}');
  assert.deepEqual(result, {
    ok: true,
    message: { v: 1, type: "navigate", path: "a/b.html", offset: 7, index: 0, count: 2 },
  });
});

test("isValidPath accepts plain relative paths and rejects non-strings", () => {
  for (const p of ["a.html", "a/b/c.php", "src/.hidden/x.html", "a..b/c.html", "über/ä.html"]) assert.ok(isValidPath(p), p);
  for (const p of [undefined, null, 1, {}, []]) assert.equal(isValidPath(p), false);
});

test("parseSrcAttribute", () => {
  assert.deepEqual(parseSrcAttribute("src/index.html:120"), { path: "src/index.html", offset: 120 });
  assert.deepEqual(parseSrcAttribute("a.html:0"), { path: "a.html", offset: 0 });
  for (const bad of [
    null, "", "a.html", "a.html:", ":5", "a.html:-1", "a.html:01", "a.html:1.5", "a.html:99999999999",
    "a.html:2147483648", "C:/a.html:5", "../a.html:5", "/a.html:5", "a.html:5:6", "a.html:5 ", "a\u0000.html:5",
    "x".repeat(600) + ":1",
  ]) {
    assert.equal(parseSrcAttribute(bad), null, String(bad));
  }
});

test("builders produce messages that parse back", () => {
  const nav = buildNavigate({ path: "a.html", offset: 3, label: "div.x", index: 0, count: 2 });
  assert.ok(nav);
  assert.equal(parsePageMessage(nav).ok, true);
  for (const raw of [buildHello(), buildHello({ id: "x1" }), buildUnmappable({ tag: "tbody", ancestor: { path: "a.html", offset: 1 } })]) {
    assert.ok(raw);
    assert.equal(parsePageMessage(raw).ok, true);
  }
  for (const raw of [buildHighlight({ path: "a.html", offset: 3, index: 1 }), buildClear(), buildAck(), buildAck({ ref: "n1" }), buildError({ code: "denied", ref: "n1" })]) {
    assert.ok(raw);
    assert.equal(parseIdeMessage(raw).ok, true);
  }
});

test("builders return null for invalid content instead of emitting it", () => {
  assert.equal(buildNavigate({ path: "../etc/passwd", offset: 1 }), null);
  assert.equal(buildNavigate({ path: "a.html", offset: -1 }), null);
  assert.equal(buildNavigate({ path: "a.html", offset: 1, label: "x".repeat(500) }), null);
  assert.equal(buildHighlight({ path: "/abs", offset: 1 }), null);
  assert.equal(buildHello({ id: "a b" }), null);
});

test("error codes list is closed and ends with IDE-only outcomes", () => {
  assert.deepEqual([...ERROR_CODES], [
    "bad_json", "too_large", "bad_version", "unknown_type", "unknown_field", "bad_field", "bad_path", "not_found", "denied",
  ]);
});

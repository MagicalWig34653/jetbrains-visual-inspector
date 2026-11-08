import assert from "node:assert/strict";
import { test } from "node:test";
import { buildSelection, findMapped, instancePosition, instancesOf, interpretIdeMessage, labelOf, locationOf } from "./core.ts";
import type { ElementLike } from "./core.ts";
import { buildClear, buildHighlight, parsePageMessage } from "../protocol.ts";

class El implements ElementLike {
  readonly tagName: string;
  parentElement: El | null;
  private readonly attrs: Record<string, string>;
  constructor(tagName: string, attrs: Record<string, string>, parent: El | null = null) {
    this.tagName = tagName;
    this.attrs = attrs;
    this.parentElement = parent;
  }
  getAttribute(name: string): string | null {
    return Object.hasOwn(this.attrs, name) ? (this.attrs[name] ?? null) : null;
  }
}

const parse = (raw: string | null) => {
  assert.ok(raw);
  const r = parsePageMessage(raw);
  assert.ok(r.ok);
  return r.message;
};

test("locationOf accepts valid and rejects invalid data-vi-src", () => {
  assert.deepEqual(locationOf(new El("DIV", { "data-vi-src": "a/b.html:12" })), { path: "a/b.html", offset: 12 });
  for (const bad of ["../x.html:1", "a.html", "a.html:01", "/abs:1", "a:b:1", ""]) {
    assert.equal(locationOf(new El("DIV", { "data-vi-src": bad })), null, bad);
  }
  assert.equal(locationOf(new El("DIV", {})), null);
});

test("findMapped returns self, nearest ancestor, or null", () => {
  const top = new El("MAIN", { "data-vi-src": "i.html:5" });
  const mid = new El("DIV", { "data-vi-src": "bad" }, top);
  const leaf = new El("SPAN", {}, mid);
  assert.equal(findMapped(leaf)?.element, top);
  assert.equal(findMapped(top)?.element, top);
  assert.equal(findMapped(new El("P", {})), null);
});

test("navigate carries location, label, index and count for repeated elements", () => {
  const list = new El("UL", {});
  const items = [0, 1, 2].map(() => new El("LI", { "data-vi-src": "t.html:40", class: "card big" }, list));
  const other = new El("LI", { "data-vi-src": "t.html:99" }, list);
  const all = [items[0], other, items[1], items[2]] as El[];
  const msg = parse(buildSelection(items[2] as El, () => all));
  assert.deepEqual(msg, { v: 1, type: "navigate", path: "t.html", offset: 40, label: "li.card", index: 2, count: 3 });
});

test("single instance gets index 0 and count 1", () => {
  const el = new El("DIV", { "data-vi-src": "a.html:1" });
  const msg = parse(buildSelection(el, () => [el]));
  assert.equal(msg.type === "navigate" && msg.index, 0);
  assert.equal(msg.type === "navigate" && msg.count, 1);
});

test("unmappable element reports nearest mapped ancestor, never guesses", () => {
  const root = new El("MAIN", { "data-vi-src": "i.html:7", class: "app" });
  const wrap = new El("DIV", { "data-vi-src": "../evil:1" }, root);
  const leaf = new El("SPAN", { class: "x" }, wrap);
  const msg = parse(buildSelection(leaf, () => []));
  assert.deepEqual(msg, {
    v: 1,
    type: "unmappable",
    tag: "span",
    label: "span.x",
    ancestor: { path: "i.html", offset: 7, label: "main.app" },
  });
});

test("unmappable without any mapped ancestor has no ancestor", () => {
  const msg = parse(buildSelection(new El("DIV", {}), () => []));
  assert.deepEqual(msg, { v: 1, type: "unmappable", tag: "div", label: "div" });
});

test("hostile class names and tags are dropped from labels", () => {
  assert.equal(labelOf(new El("DIV", { class: "a‮b" })), "div");
  assert.equal(labelOf(new El("DIV", { class: "x".repeat(200) })), "div");
  assert.equal(buildSelection(new El("1BAD:TAG", {}), () => []), null);
});

test("instancePosition and instancesOf", () => {
  const a = new El("A", { "data-vi-src": "p.html:1" });
  const b = new El("B", { "data-vi-src": "p.html:1" });
  const c = new El("C", { "data-vi-src": "p.html:2" });
  const found = instancesOf([a, c, b], { path: "p.html", offset: 1 });
  assert.deepEqual(found, [a, b]);
  assert.deepEqual(instancePosition(b, found), { index: 1, count: 2 });
  assert.equal(instancePosition(c, found), null);
});

test("interpretIdeMessage accepts valid highlight and clear, drops everything else", () => {
  assert.deepEqual(interpretIdeMessage(buildHighlight({ path: "a.html", offset: 3, index: 1 })), {
    kind: "highlight",
    location: { path: "a.html", offset: 3 },
    index: 1,
  });
  assert.deepEqual(interpretIdeMessage(buildHighlight({ path: "a.html", offset: 3 })), {
    kind: "highlight",
    location: { path: "a.html", offset: 3 },
  });
  assert.deepEqual(interpretIdeMessage(buildClear()), { kind: "clear" });
  for (const bad of [
    "not json",
    42,
    '{"v":1,"type":"highlight","path":"../x","offset":1}',
    '{"v":1,"type":"highlight","path":"a.html","offset":1,"extra":1}',
    '{"v":2,"type":"clear"}',
    '{"v":1,"type":"navigate","path":"a.html","offset":1}',
    '{"v":1,"type":"ack"}',
  ]) {
    assert.deepEqual(interpretIdeMessage(bad), { kind: "ignore" }, String(bad));
  }
});

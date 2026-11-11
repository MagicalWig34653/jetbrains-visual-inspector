/**
 * DOM layer of the inspector against a small hand-written DOM stub (no jsdom: the layer only needs
 * createElement, a closed shadow root, a few events and rAF). Real-browser behavior is covered by the
 * manual runIde checklist in docs/research/test-report.md.
 */
import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import { startInspector } from "./inspector.ts";
import type { InspectorHandle, Transport } from "./inspector.ts";

class StubElement {
  tagName: string;
  attrs = new Map<string, string>();
  children: StubElement[] = [];
  parentElement: StubElement | null = null;
  className = "";
  textContent = "";
  style: Record<string, string> = {};
  isConnected = true;
  rect = { left: 10, top: 20, width: 30, height: 40 };
  scrolledIntoView: unknown[] = [];
  shadowMode: string | undefined;
  shadow: StubElement | undefined;
  constructor(tag: string) {
    this.tagName = tag.toUpperCase();
  }
  append(...nodes: StubElement[]): void {
    for (const n of nodes) {
      n.parentElement = this;
      this.children.push(n);
    }
  }
  remove(): void {
    const siblings = this.parentElement?.children;
    if (siblings) siblings.splice(siblings.indexOf(this), 1);
    this.parentElement = null;
    this.isConnected = false;
  }
  attachShadow(init: { mode: string }): StubElement {
    this.shadowMode = init.mode;
    this.shadow = new StubElement("#shadow");
    return this.shadow;
  }
  getAttribute(name: string): string | null {
    return this.attrs.get(name) ?? null;
  }
  getBoundingClientRect() {
    return this.rect;
  }
  scrollIntoView(arg: unknown): void {
    this.scrolledIntoView.push(arg);
  }
  withAttr(name: string, value: string): this {
    this.attrs.set(name, value);
    return this;
  }
  child(tag: string, attr?: string): StubElement {
    const el = new StubElement(tag);
    if (attr) el.attrs.set("data-vi-src", attr);
    this.append(el);
    return el;
  }
  descendants(): StubElement[] {
    return this.children.flatMap((c) => [c, ...c.descendants()]);
  }
}

interface FakeEvent {
  target: unknown;
  isTrusted: boolean;
  altKey: boolean;
  defaultPrevented: boolean;
  propagationStopped: boolean;
  preventDefault(): void;
  stopPropagation(): void;
}
function event(target: unknown, opts: { trusted?: boolean; alt?: boolean } = {}): FakeEvent {
  return {
    target,
    isTrusted: opts.trusted ?? true,
    altKey: opts.alt ?? false,
    defaultPrevented: false,
    propagationStopped: false,
    preventDefault() { this.defaultPrevented = true; },
    stopPropagation() { this.propagationStopped = true; },
  };
}

type Listener = (e: FakeEvent) => void;
let listeners: Map<string, { fn: Listener; capture: unknown }[]>;
let frames: Map<number, () => void>;
let nextFrame: number;
let html: StubElement;
let body: StubElement;
let cancelled: number[];

function installDom(): void {
  listeners = new Map();
  frames = new Map();
  cancelled = [];
  nextFrame = 1;
  html = new StubElement("html");
  body = html.child("body");
  const win = {
    scrollX: 5,
    scrollY: 7,
    addEventListener(type: string, fn: Listener, capture?: unknown) {
      listeners.set(type, [...(listeners.get(type) ?? []), { fn, capture }]);
    },
    removeEventListener(type: string, fn: Listener, capture?: unknown) {
      listeners.set(type, (listeners.get(type) ?? []).filter((l) => l.fn !== fn || l.capture !== capture));
    },
    requestAnimationFrame(cb: () => void) {
      frames.set(nextFrame, cb);
      return nextFrame++;
    },
    cancelAnimationFrame(id: number) {
      cancelled.push(id);
      frames.delete(id);
    },
  };
  const doc = {
    documentElement: html,
    createElement: (tag: string) => new StubElement(tag),
    querySelectorAll: (selector: string) => {
      assert.equal(selector, "[data-vi-src]");
      return html.descendants().filter((e) => e.attrs.has("data-vi-src"));
    },
  };
  Object.assign(globalThis, { window: win, document: doc, Element: StubElement });
}

function dispatch(type: string, e: FakeEvent): void {
  for (const l of [...(listeners.get(type) ?? [])]) l.fn(e);
}
function flushFrames(): void {
  const pending = [...frames.values()];
  frames.clear();
  for (const cb of pending) cb();
}
const count = (type: string) => listeners.get(type)?.length ?? 0;

interface Harness {
  sent: string[];
  modes: boolean[];
  receivers: ((m: string) => void)[];
  unsubscribed: number;
  transport: Transport;
}
function harness(): Harness {
  const h: Harness = {
    sent: [],
    modes: [],
    receivers: [],
    unsubscribed: 0,
    transport: {
      send: (m) => void h.sent.push(m),
      modeChanged: (on) => void h.modes.push(on),
      subscribe: (r) => {
        h.receivers.push(r);
        return () => void h.unsubscribed++;
      },
    },
  };
  return h;
}

const overlayHost = () => html.children.find((c) => c.shadow !== undefined)!;
const overlayRoot = () => overlayHost().shadow!.children[1]!; // [style, root]
const hoverBox = () => overlayRoot().children[0]!;
const tip = () => overlayRoot().children[1]!;
const highlightBoxes = () => overlayRoot().children.slice(2);

let h: Harness;
let inspector: InspectorHandle;

beforeEach(() => {
  installDom();
  h = harness();
});
afterEach(() => {
  inspector?.dispose();
  for (const key of ["window", "document", "Element"]) delete (globalThis as Record<string, unknown>)[key];
});

test("start: closed shadow root host in the document, hello sent, inspect mode off, only click listened", () => {
  inspector = startInspector(h.transport);
  assert.equal(overlayHost().shadowMode, "closed");
  assert.equal(overlayHost().parentElement, html);
  assert.deepEqual(h.sent, ['{"v":1,"type":"hello"}']);
  assert.equal(inspector.isInspectMode(), false);
  assert.equal(count("click"), 1);
  assert.equal(listeners.get("click")?.[0]?.capture, true);
  assert.equal(count("pointermove"), 0);
});

test("start does not touch the page's own elements", () => {
  const el = body.child("div", "a.html:1");
  inspector = startInspector(h.transport);
  assert.deepEqual([...el.attrs], [["data-vi-src", "a.html:1"]]);
  assert.deepEqual(el.style, {});
  assert.deepEqual(body.children, [el]);
});

test("start survives a transport that throws everywhere", () => {
  const bad: Transport = {
    send() { throw new Error("send"); },
    subscribe() { throw new Error("subscribe"); },
    modeChanged() { throw new Error("mode"); },
  };
  inspector = startInspector(bad);
  inspector.setInspectMode(true);
  const el = body.child("p", "a.html:3");
  assert.doesNotThrow(() => dispatch("click", event(el)));
  assert.equal(inspector.isInspectMode(), true);
});

test("click while inspect mode is off passes through untouched", () => {
  const el = body.child("p", "a.html:3");
  inspector = startInspector(h.transport);
  const e = event(el);
  dispatch("click", e);
  assert.equal(e.defaultPrevented, false);
  assert.equal(e.propagationStopped, false);
  assert.deepEqual(h.sent, ['{"v":1,"type":"hello"}']);
});

test("click in inspect mode sends navigate and swallows the click", () => {
  const el = body.child("p", "a.html:3");
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  const e = event(el);
  dispatch("click", e);
  assert.equal(e.defaultPrevented && e.propagationStopped, true);
  assert.deepEqual(JSON.parse(h.sent.at(-1)!), {
    v: 1, type: "navigate", path: "a.html", offset: 3, label: "p", index: 0, count: 1,
  });
});

test("click on a repeated component reports its own instance index and the count", () => {
  const list = body.child("ul");
  list.child("li", "a.html:9");
  const second = list.child("li", "a.html:9");
  list.child("li", "a.html:9");
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  dispatch("click", event(second));
  const m = JSON.parse(h.sent.at(-1)!);
  assert.equal(m.index, 1);
  assert.equal(m.count, 3);
});

test("click on an unmappable element sends unmappable with the nearest mapped ancestor", () => {
  const card = body.child("section", "a.html:50");
  const generated = card.child("div").child("span");
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  dispatch("click", event(generated));
  assert.deepEqual(JSON.parse(h.sent.at(-1)!), {
    v: 1, type: "unmappable", tag: "span", label: "span", ancestor: { path: "a.html", offset: 50, label: "section" },
  });
});

test("click on an element with a tampered attribute is unmappable, never navigate", () => {
  const evil = body.child("p", "../../etc/passwd:3");
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  dispatch("click", event(evil));
  assert.equal(JSON.parse(h.sent.at(-1)!).type, "unmappable");
});

test("untrusted (script-dispatched) clicks neither select nor toggle", () => {
  const el = body.child("p", "a.html:3");
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  const before = h.sent.length;
  const plain = event(el, { trusted: false });
  dispatch("click", plain);
  const alt = event(el, { trusted: false, alt: true });
  dispatch("click", alt);
  assert.equal(h.sent.length, before);
  assert.equal(inspector.isInspectMode(), true);
  assert.equal(plain.defaultPrevented || alt.defaultPrevented, false);
});

test("click on the overlay host itself or a non-element target is ignored", () => {
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  const before = h.sent.length;
  dispatch("click", event(overlayHost()));
  dispatch("click", event({ not: "an element" }));
  assert.equal(h.sent.length, before);
});

test("alt+click toggles inspect mode both ways, reports it and is swallowed", () => {
  const el = body.child("p", "a.html:3");
  inspector = startInspector(h.transport);
  const on = event(el, { alt: true });
  dispatch("click", on);
  assert.equal(inspector.isInspectMode(), true);
  assert.equal(on.defaultPrevented && on.propagationStopped, true);
  dispatch("click", event(el, { alt: true }));
  assert.equal(inspector.isInspectMode(), false);
  assert.deepEqual(h.modes, [true, false]);
  assert.deepEqual(h.sent, ['{"v":1,"type":"hello"}']); // toggling never sends a selection
});

test("setInspectMode is idempotent and registers pointermove exactly once", () => {
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  inspector.setInspectMode(true);
  assert.equal(count("pointermove"), 1);
  assert.deepEqual(h.modes, [true]);
  inspector.setInspectMode(false);
  inspector.setInspectMode(false);
  assert.equal(count("pointermove"), 0);
  assert.deepEqual(h.modes, [true, false]);
});

test("hover: many moves in one frame cause one repaint for the last target", () => {
  const a = body.child("p", "a.html:1");
  const b = body.child("p", "a.html:2");
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  dispatch("pointermove", event(a));
  dispatch("pointermove", event(b));
  dispatch("pointermove", event(a));
  dispatch("pointermove", event(b));
  assert.equal(frames.size, 1);
  assert.equal(hoverBox().style.display, "none"); // nothing painted before the frame
  assert.equal(tip().style.display, "none");
  flushFrames();
  assert.equal(tip().textContent, "p  a.html:2");
  assert.equal(tip().style.display, "block");
  assert.equal(hoverBox().style.display, "block");
  assert.equal(hoverBox().style.left, "15px"); // rect.left + scrollX
  assert.equal(hoverBox().style.top, "27px"); // rect.top + scrollY
  dispatch("pointermove", event(a));
  assert.equal(frames.size, 1); // a new frame can be scheduled after the flush
});

test("hover labels: ancestor-mapped and unmapped elements are described honestly", () => {
  const card = body.child("div", "a.html:5");
  const inner = card.child("span");
  const orphan = body.child("em");
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  dispatch("pointermove", event(inner));
  flushFrames();
  assert.equal(tip().textContent, "span  a.html:5  (ancestor)");
  dispatch("pointermove", event(orphan));
  flushFrames();
  assert.equal(tip().textContent, "em  (no source location)");
});

test("hover ignores untrusted moves and the overlay host", () => {
  const a = body.child("p", "a.html:1");
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  dispatch("pointermove", event(a, { trusted: false }));
  assert.equal(frames.size, 0); // an untrusted move does not even schedule a frame
  dispatch("pointermove", event(overlayHost()));
  flushFrames();
  assert.equal(tip().style.display, "none");
});

test("hover on an element removed before the frame hides the overlay", () => {
  const a = body.child("p", "a.html:1");
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  dispatch("pointermove", event(a));
  a.remove();
  flushFrames();
  assert.equal(tip().style.display, "none");
  assert.equal(hoverBox().style.display, "none");
});

test("turning inspect mode off cancels the pending frame and hides the hover", () => {
  const a = body.child("p", "a.html:1");
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  dispatch("pointermove", event(a));
  flushFrames();
  dispatch("pointermove", event(a));
  inspector.setInspectMode(false);
  assert.equal(cancelled.length, 1);
  assert.equal(frames.size, 0);
  assert.equal(tip().style.display, "none");
  assert.equal(hoverBox().style.display, "none");
});

test("highlight from the IDE marks every instance of a repeated component and emphasizes one", () => {
  const first = body.child("li", "a.html:9");
  const second = body.child("li", "a.html:9");
  body.child("li", "a.html:10");
  inspector = startInspector(h.transport);
  inspector.receive('{"v":1,"type":"highlight","path":"a.html","offset":9,"index":1}');
  const boxes = highlightBoxes();
  assert.equal(boxes.length, 2);
  assert.deepEqual(boxes.map((b) => b.className), ["box hl", "box hl em"]);
  assert.equal(second.scrolledIntoView.length, 1);
  assert.equal(first.scrolledIntoView.length, 0);
  assert.equal(boxes[0]!.style.left, "15px");
});

test("highlight without index or with an out-of-range index emphasizes nothing and does not scroll", () => {
  const el = body.child("li", "a.html:9");
  inspector = startInspector(h.transport);
  inspector.receive('{"v":1,"type":"highlight","path":"a.html","offset":9}');
  inspector.receive('{"v":1,"type":"highlight","path":"a.html","offset":9,"index":5}');
  assert.deepEqual(highlightBoxes().map((b) => b.className), ["box hl"]);
  assert.equal(el.scrolledIntoView.length, 0);
});

test("a new highlight replaces the previous one; clear removes all boxes", () => {
  body.child("li", "a.html:9");
  body.child("li", "a.html:11");
  inspector = startInspector(h.transport);
  inspector.receive('{"v":1,"type":"highlight","path":"a.html","offset":9}');
  inspector.receive('{"v":1,"type":"highlight","path":"a.html","offset":11}');
  assert.equal(highlightBoxes().length, 1);
  inspector.receive('{"v":1,"type":"clear"}');
  assert.equal(highlightBoxes().length, 0);
});

test("highlight for a location that is not in the page draws nothing", () => {
  body.child("li", "a.html:9");
  inspector = startInspector(h.transport);
  inspector.receive('{"v":1,"type":"highlight","path":"a.html","offset":9}');
  inspector.receive('{"v":1,"type":"highlight","path":"other.html","offset":9}');
  assert.equal(highlightBoxes().length, 0);
});

test("highlight draws at most 200 boxes", () => {
  for (let i = 0; i < 250; i++) body.child("li", "a.html:9");
  inspector = startInspector(h.transport);
  inspector.receive('{"v":1,"type":"highlight","path":"a.html","offset":9}');
  assert.equal(highlightBoxes().length, 200);
});

test("invalid, page-direction or non-actionable IDE messages change nothing", () => {
  body.child("li", "a.html:9");
  inspector = startInspector(h.transport);
  inspector.receive('{"v":1,"type":"highlight","path":"a.html","offset":9}');
  for (const bad of [
    "", "not json", '{"v":1,"type":"navigate","path":"a.html","offset":9}',
    '{"v":1,"type":"highlight","path":"../x.html","offset":9}', '{"v":1,"type":"ack"}',
    '{"v":1,"type":"highlight","path":"a.html","offset":9.0}', "x".repeat(5000),
  ]) inspector.receive(bad);
  assert.equal(highlightBoxes().length, 1);
});

test("the subscribed receiver is wired to the same validation", () => {
  body.child("li", "a.html:9");
  inspector = startInspector(h.transport);
  assert.equal(h.receivers.length, 1);
  h.receivers[0]!('{"v":1,"type":"highlight","path":"a.html","offset":9}');
  assert.equal(highlightBoxes().length, 1);
});

test("highlights and hover react to DOM changes after start (dynamic content)", () => {
  inspector = startInspector(h.transport);
  inspector.receive('{"v":1,"type":"highlight","path":"a.html","offset":9}');
  assert.equal(highlightBoxes().length, 0);
  body.child("li", "a.html:9"); // rendered later by a framework
  inspector.receive('{"v":1,"type":"highlight","path":"a.html","offset":9}');
  assert.equal(highlightBoxes().length, 1);
  inspector.setInspectMode(true);
  dispatch("click", event(body.child("li", "a.html:9")));
  assert.equal(JSON.parse(h.sent.at(-1)!).count, 2);
});

test("dispose removes listeners, host and boxes, unsubscribes, and later calls are inert", () => {
  body.child("li", "a.html:9");
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  inspector.receive('{"v":1,"type":"highlight","path":"a.html","offset":9}');
  dispatch("pointermove", event(body.children[0]));
  inspector.dispose();
  assert.equal(count("click"), 0);
  assert.equal(count("pointermove"), 0);
  assert.equal(html.children.some((c) => c.shadow !== undefined), false);
  assert.equal(h.unsubscribed, 1);
  assert.equal(inspector.isInspectMode(), false);
  assert.equal(cancelled.length, 1);
  const sentBefore = h.sent.length;
  const modesBefore = h.modes.length;
  inspector.setInspectMode(true);
  inspector.receive('{"v":1,"type":"highlight","path":"a.html","offset":9}');
  inspector.dispose(); // idempotent
  assert.equal(inspector.isInspectMode(), false);
  assert.equal(h.sent.length, sentBefore);
  assert.equal(h.modes.length, modesBefore);
  assert.equal(h.unsubscribed, 1);
  assert.equal(count("pointermove"), 0);
});

test("dispose while a frame is pending never paints afterwards", () => {
  const a = body.child("p", "a.html:1");
  inspector = startInspector(h.transport);
  inspector.setInspectMode(true);
  dispatch("pointermove", event(a));
  inspector.dispose();
  assert.doesNotThrow(flushFrames);
});

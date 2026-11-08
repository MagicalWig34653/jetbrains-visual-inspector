/**
 * Inspector overlay (DOM layer). Read-only toward the page apart from its own overlay, which lives
 * in a closed shadow root. The only entry point is `startInspector(transport)`.
 */
import { buildHello } from "../protocol.ts";
import { buildSelection, findMapped, instancesOf, interpretIdeMessage, SRC_ATTRIBUTE } from "./core.ts";
import type { Location } from "./core.ts";

/** Supplied by the host (for example a function wrapping JCEF's query object). Never taken from the page. */
export interface Transport {
  /** Sends one serialized protocol message to the IDE. Must not throw. */
  send(message: string): void;
  /** Optional: register a receiver for serialized IDE messages. Returns an unsubscribe function. */
  subscribe?(receive: (message: string) => void): () => void;
}

export interface InspectorHandle {
  setInspectMode(on: boolean): void;
  isInspectMode(): boolean;
  /** Feeds a raw IDE message (validated here). */
  receive(raw: string): void;
  dispose(): void;
}

const MAX_BOXES = 200;
const STYLE = `
:host { all: initial; }
.root { position: absolute; top: 0; left: 0; width: 0; height: 0; pointer-events: none; z-index: 2147483647; }
.box { position: absolute; box-sizing: border-box; pointer-events: none; border: 2px solid #3d7eff; background: rgba(61,126,255,.18); }
.box.hl { border-color: #ff8a1f; background: rgba(255,138,31,.2); }
.box.em { border-width: 3px; background: rgba(255,138,31,.35); }
.tip { position: absolute; pointer-events: none; font: 12px/1.3 ui-monospace, monospace; color: #fff; background: #1e1f22; padding: 3px 6px; border-radius: 3px; white-space: nowrap; max-width: 80vw; overflow: hidden; text-overflow: ellipsis; }
`;

export function startInspector(transport: Transport): InspectorHandle {
  const doc = document;
  const win = window;
  let inspect = false;
  let disposed = false;
  let pending: Element | null = null;
  let frame = 0;
  let unsubscribe: (() => void) | undefined;

  const host = doc.createElement("div");
  const shadow = host.attachShadow({ mode: "closed" });
  const style = doc.createElement("style");
  style.textContent = STYLE;
  const root = doc.createElement("div");
  root.className = "root";
  const hoverBox = doc.createElement("div");
  hoverBox.className = "box";
  hoverBox.style.display = "none";
  const tip = doc.createElement("div");
  tip.className = "tip";
  tip.style.display = "none";
  root.append(hoverBox, tip);
  shadow.append(style, root);
  const hlBoxes: HTMLElement[] = [];

  const mapped = (el: Element) => findMapped(el);
  const allMapped = () => doc.querySelectorAll(`[${SRC_ATTRIBUTE}]`);

  function place(box: HTMLElement, el: Element): void {
    const r = el.getBoundingClientRect();
    box.style.left = `${r.left + win.scrollX}px`;
    box.style.top = `${r.top + win.scrollY}px`;
    box.style.width = `${r.width}px`;
    box.style.height = `${r.height}px`;
    box.style.display = "block";
  }

  function hideHover(): void {
    hoverBox.style.display = "none";
    tip.style.display = "none";
  }

  function paintHover(): void {
    frame = 0;
    const el = pending;
    pending = null;
    if (disposed || !inspect || !el || !el.isConnected) return hideHover();
    const m = mapped(el);
    place(hoverBox, el);
    tip.textContent = m
      ? `${el.tagName.toLowerCase()}  ${m.location.path}:${m.location.offset}${m.element === el ? "" : "  (ancestor)"}`
      : `${el.tagName.toLowerCase()}  (no source location)`;
    tip.style.display = "block";
    const r = el.getBoundingClientRect();
    tip.style.left = `${Math.max(0, r.left + win.scrollX)}px`;
    tip.style.top = `${Math.max(0, r.top + win.scrollY - 24)}px`;
  }

  function targetOf(event: Event): Element | null {
    const t = event.target;
    return t instanceof Element && t !== host ? t : null;
  }

  function onMove(event: Event): void {
    if (!event.isTrusted) return;
    pending = targetOf(event);
    if (!frame) frame = win.requestAnimationFrame(paintHover);
  }

  function onClick(event: MouseEvent): void {
    if (!event.isTrusted) return; // page scripts cannot synthesize selections
    if (event.altKey) {
      event.preventDefault();
      event.stopPropagation();
      setInspectMode(!inspect);
      return;
    }
    if (!inspect) return;
    event.preventDefault();
    event.stopPropagation();
    const el = targetOf(event);
    if (!el) return;
    const message = buildSelection(el, () => allMapped() as Iterable<Element>);
    if (message) safeSend(message);
  }

  function safeSend(message: string): void {
    try {
      transport.send(message);
    } catch {
      // host transport failure must not break the page
    }
  }

  function clearHighlights(): void {
    for (const b of hlBoxes) b.remove();
    hlBoxes.length = 0;
  }

  function highlight(location: Location, index?: number): void {
    clearHighlights();
    const found = instancesOf(allMapped() as Iterable<Element>, location).slice(0, MAX_BOXES);
    const emphasized = index !== undefined && index < found.length ? index : -1;
    found.forEach((el, i) => {
      const box = doc.createElement("div");
      box.className = i === emphasized ? "box hl em" : "box hl";
      place(box, el);
      root.append(box);
      hlBoxes.push(box);
    });
    if (emphasized >= 0) found[emphasized]?.scrollIntoView({ block: "center", inline: "nearest" });
  }

  function receive(raw: string): void {
    if (disposed) return;
    const action = interpretIdeMessage(raw);
    if (action.kind === "clear") clearHighlights();
    else if (action.kind === "highlight") highlight(action.location, action.index);
  }

  function setInspectMode(on: boolean): void {
    if (disposed || on === inspect) return;
    inspect = on;
    if (on) {
      win.addEventListener("pointermove", onMove, true);
    } else {
      win.removeEventListener("pointermove", onMove, true);
      if (frame) win.cancelAnimationFrame(frame);
      frame = 0;
      pending = null;
      hideHover();
    }
  }

  function dispose(): void {
    if (disposed) return;
    setInspectMode(false);
    disposed = true;
    win.removeEventListener("click", onClick, true);
    unsubscribe?.();
    clearHighlights();
    host.remove();
  }

  win.addEventListener("click", onClick, true);
  (doc.documentElement ?? doc.body).append(host);
  try {
    unsubscribe = transport.subscribe?.(receive);
  } catch {
    // no receiver: page-to-IDE still works
  }
  const hello = buildHello();
  if (hello) safeSend(hello);

  return { setInspectMode, isInspectMode: () => inspect, receive, dispose };
}

/**
 * DOM-independent inspector logic. Works on minimal element shapes so it can be tested without a
 * DOM. Everything read from the page is untrusted: attributes are validated with the protocol
 * parser, labels are sanitized, and nothing is guessed.
 */
import {
  buildNavigate,
  buildUnmappable,
  parseIdeMessage,
  parseSrcAttribute,
  LIMITS,
} from "../protocol.ts";

export const SRC_ATTRIBUTE = "data-vi-src";

/** The subset of Element the core needs. */
export interface ElementLike {
  readonly tagName: string;
  readonly parentElement: ElementLike | null;
  getAttribute(name: string): string | null;
}

export interface Location {
  path: string;
  offset: number;
}

export interface Mapped<E> {
  element: E;
  location: Location;
}

/** Location of one element from its own attribute, or null if missing or invalid. */
export function locationOf(element: ElementLike): Location | null {
  let value: string | null;
  try {
    value = element.getAttribute(SRC_ATTRIBUTE);
  } catch {
    return null;
  }
  return value === null ? null : parseSrcAttribute(value);
}

/** The element itself or its nearest ancestor with a valid location. */
export function findMapped<E extends ElementLike>(start: E): Mapped<E> | null {
  let current: ElementLike | null = start;
  // Depth cap protects against pathological or cyclic parent chains.
  for (let depth = 0; current && depth < 10000; depth++) {
    const location = locationOf(current);
    if (location) return { element: current as E, location };
    current = current.parentElement;
  }
  return null;
}

export function sameLocation(a: Location, b: Location): boolean {
  return a.path === b.path && a.offset === b.offset;
}

/** Elements (from `candidates`, in document order) whose location equals `location`. */
export function instancesOf<E extends ElementLike>(candidates: Iterable<E>, location: Location): E[] {
  const result: E[] = [];
  for (const candidate of candidates) {
    const other = locationOf(candidate);
    if (other && sameLocation(other, location)) result.push(candidate);
  }
  return result;
}

/** Instance index and count of `element` among `instances`, or null if it is not listed or too many. */
export function instancePosition<E>(element: E, instances: readonly E[]): { index: number; count: number } | null {
  const index = instances.indexOf(element);
  if (index < 0 || instances.length > LIMITS.maxInstances) return null;
  return { index, count: instances.length };
}

/** Short display label such as `div.card`. Returns undefined when nothing safe remains. */
export function labelOf(element: ElementLike): string | undefined {
  const tag = element.tagName.toLowerCase();
  if (!/^[a-z][a-z0-9-]*$/.test(tag)) return undefined;
  let cls = "";
  try {
    const first = (element.getAttribute("class") ?? "").trim().split(/\s+/)[0] ?? "";
    if (/^[A-Za-z_][A-Za-z0-9_-]{0,63}$/.test(first)) cls = "." + first;
  } catch {
    // ignore: label falls back to the tag
  }
  return tag + cls;
}

/** Tag name valid for the protocol, or undefined. */
function tagOf(element: ElementLike): string | undefined {
  const tag = element.tagName.toLowerCase();
  return /^[a-z][a-z0-9-]*$/.test(tag) && tag.length <= LIMITS.maxTagChars ? tag : undefined;
}

/**
 * Builds the message for a clicked element: `navigate` if it has a location, otherwise
 * `unmappable` with the nearest mapped ancestor. Returns null if nothing valid can be built.
 */
export function buildSelection<E extends ElementLike>(
  target: E,
  allElements: () => Iterable<E>,
): string | null {
  const own = locationOf(target);
  if (own) {
    const position = instancePosition(target, instancesOf(allElements(), own));
    const label = labelOf(target);
    const body = { ...own, ...(label ? { label } : {}), ...(position ?? {}) };
    return buildNavigate(body) ?? buildNavigate({ ...own });
  }
  const tag = tagOf(target);
  if (!tag) return null;
  const label = labelOf(target);
  const ancestor = target.parentElement ? findMapped(target.parentElement) : null;
  const ancestorLabel = ancestor ? labelOf(ancestor.element) : undefined;
  const body = {
    tag,
    ...(label ? { label } : {}),
    ...(ancestor ? { ancestor: { ...ancestor.location, ...(ancestorLabel ? { label: ancestorLabel } : {}) } } : {}),
  };
  return buildUnmappable(body) ?? buildUnmappable({ tag });
}

export type IdeAction =
  | { kind: "highlight"; location: Location; index?: number }
  | { kind: "clear" }
  | { kind: "ignore" };

/** Validates a raw IDE message; invalid or non-actionable messages become `ignore`. */
export function interpretIdeMessage(raw: unknown): IdeAction {
  const parsed = parseIdeMessage(raw);
  if (!parsed.ok) return { kind: "ignore" };
  const m = parsed.message;
  if (m.type === "clear") return { kind: "clear" };
  if (m.type === "highlight") {
    const location = { path: m.path, offset: m.offset };
    return m.index === undefined ? { kind: "highlight", location } : { kind: "highlight", location, index: m.index };
  }
  return { kind: "ignore" };
}

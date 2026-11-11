/**
 * Bridge protocol v1 (normative spec: docs/protocol.md, shared vectors:
 * test-fixtures/protocol/vectors.json). Pure functions, no dependencies.
 *
 * Both sides treat every incoming string as untrusted: parse functions never throw, they return
 * `{ ok: false, code }`. Anything outside the closed schema is rejected, not repaired.
 * Final canonicalization of a path into the project happens in the IDE, not here.
 */
export const PROTOCOL_VERSION = 1;

export const LIMITS = {
  maxMessageChars: 4096,
  maxPathChars: 512,
  maxSegmentChars: 255,
  maxLabelChars: 128,
  maxTagChars: 32,
  maxIdChars: 64,
  maxInt: 2147483647,
  maxInstances: 10000,
} as const;

export const ERROR_CODES = [
  "bad_json",
  "too_large",
  "bad_version",
  "unknown_type",
  "unknown_field",
  "bad_field",
  "bad_path",
  "not_found",
  "denied",
] as const;
export type ErrorCode = (typeof ERROR_CODES)[number];

/** Codes a receiver reports for an invalid message (the rest are IDE navigation outcomes). */
export type ParseErrorCode = Exclude<ErrorCode, "not_found" | "denied">;

export interface AncestorLocation {
  path: string;
  offset: number;
  label?: string;
}

export type PageMessage =
  | { v: 1; type: "hello"; id?: string }
  | {
      v: 1;
      type: "navigate";
      path: string;
      offset: number;
      line?: number;
      label?: string;
      index?: number;
      count?: number;
      id?: string;
    }
  | { v: 1; type: "unmappable"; tag: string; label?: string; ancestor?: AncestorLocation; id?: string };

export type IdeMessage =
  | { v: 1; type: "highlight"; path: string; offset: number; index?: number; id?: string }
  | { v: 1; type: "clear"; id?: string }
  | { v: 1; type: "ack"; ref?: string }
  | { v: 1; type: "error"; code: ErrorCode; ref?: string };

export type ParseResult<T> = { ok: true; message: T } | { ok: false; code: ParseErrorCode };

type Field = "id" | "ref" | "path" | "offset" | "line" | "label" | "index" | "count" | "tag" | "code" | "ancestor";
interface Shape {
  required: readonly Field[];
  optional: readonly Field[];
}

const PAGE_SHAPES: Record<string, Shape> = {
  hello: { required: [], optional: ["id"] },
  navigate: { required: ["path", "offset"], optional: ["line", "label", "index", "count", "id"] },
  unmappable: { required: ["tag"], optional: ["label", "ancestor", "id"] },
};
const IDE_SHAPES: Record<string, Shape> = {
  highlight: { required: ["path", "offset"], optional: ["index", "id"] },
  clear: { required: [], optional: ["id"] },
  ack: { required: [], optional: ["ref"] },
  error: { required: ["code"], optional: ["ref"] },
};

// Control, format (incl. bidi overrides, zero width), unpaired surrogate, line/paragraph separator.
const FORBIDDEN_CHARS = /[\p{Cc}\p{Cf}\p{Cs}\p{Zl}\p{Zp}]/u;
const ID_PATTERN = /^[A-Za-z0-9_.-]+$/;
const TAG_PATTERN = /^[A-Za-z][A-Za-z0-9-]*$/;

/** True if `path` is an acceptable project-relative path (syntax only; the IDE canonicalizes). */
export function isValidPath(path: unknown): path is string {
  if (typeof path !== "string") return false;
  if (path.length === 0 || path.length > LIMITS.maxPathChars) return false;
  if (FORBIDDEN_CHARS.test(path)) return false;
  // Backslash (Windows separator, UNC), colon (scheme, drive letter), percent (encoded dots/separators).
  if (/[\\:%]/.test(path)) return false;
  for (const segment of path.split("/")) {
    if (segment === "" || segment === "." || segment === "..") return false;
    if (segment.length > LIMITS.maxSegmentChars) return false;
    if (segment.startsWith("~") || segment.endsWith(".") || segment.endsWith(" ") || segment.startsWith(" ")) return false;
  }
  return true;
}

function isInt(value: unknown, min: number, max: number): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= min && value <= max;
}

function isText(value: unknown, max: number): value is string {
  return typeof value === "string" && value.length >= 1 && value.length <= max && !FORBIDDEN_CHARS.test(value);
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function checkAncestor(value: unknown): ParseErrorCode | null {
  if (!isPlainObject(value)) return "bad_field";
  const allowed = ["path", "offset", "label"];
  if (!Object.keys(value).every((k) => allowed.includes(k))) return "unknown_field";
  if (!Object.hasOwn(value, "path") || !Object.hasOwn(value, "offset")) return "bad_field";
  if (typeof value.path !== "string") return "bad_field";
  if (!isValidPath(value.path)) return "bad_path";
  if (!isInt(value.offset, 0, LIMITS.maxInt)) return "bad_field";
  if (Object.hasOwn(value, "label") && !isText(value.label, LIMITS.maxLabelChars)) return "bad_field";
  return null;
}

function checkField(field: Field, obj: Record<string, unknown>): ParseErrorCode | null {
  const value = obj[field];
  switch (field) {
    case "path":
      if (typeof value !== "string") return "bad_field";
      return isValidPath(value) ? null : "bad_path";
    case "offset":
      return isInt(value, 0, LIMITS.maxInt) ? null : "bad_field";
    case "line":
      return isInt(value, 1, LIMITS.maxInt) ? null : "bad_field";
    case "index":
      return isInt(value, 0, LIMITS.maxInstances - 1) ? null : "bad_field";
    case "count":
      return isInt(value, 1, LIMITS.maxInstances) ? null : "bad_field";
    case "label":
      return isText(value, LIMITS.maxLabelChars) ? null : "bad_field";
    case "tag":
      return typeof value === "string" && value.length <= LIMITS.maxTagChars && TAG_PATTERN.test(value)
        ? null
        : "bad_field";
    case "id":
    case "ref":
      return typeof value === "string" && value.length <= LIMITS.maxIdChars && ID_PATTERN.test(value)
        ? null
        : "bad_field";
    case "code":
      return typeof value === "string" && (ERROR_CODES as readonly string[]).includes(value) ? null : "bad_field";
    case "ancestor":
      return checkAncestor(value);
  }
}

const INTEGER_LITERAL = /^-?(0|[1-9][0-9]*)$/;

/**
 * Kotlin treats `1.0`, `1e0` and `-0` as non-integers; `JSON.parse` would normalize them to integers.
 * A number whose source text is not a plain integer literal becomes `null` (a non-integer for every
 * field check). Engines without JSON source text access (`context.source`) keep the lenient behavior;
 * the Kotlin validator is the security boundary.
 */
function strictIntegerReviver(_key: string, value: unknown, context?: { source?: string }): unknown {
  if (typeof value !== "number") return value;
  const source = context?.source;
  return source === undefined || (INTEGER_LITERAL.test(source) && source !== "-0") ? value : null;
}

function parseWith<T>(raw: unknown, shapes: Record<string, Shape>): ParseResult<T> {
  const fail = (code: ParseErrorCode): ParseResult<T> => ({ ok: false, code });
  try {
    if (typeof raw !== "string") return fail("bad_json");
    if (raw.length > LIMITS.maxMessageChars) return fail("too_large");
    let parsed: unknown;
    try {
      parsed = JSON.parse(raw, strictIntegerReviver);
    } catch {
      return fail("bad_json");
    }
    if (!isPlainObject(parsed)) return fail("bad_json");
    if (!Object.hasOwn(parsed, "v") || parsed.v !== PROTOCOL_VERSION) return fail("bad_version");
    if (!Object.hasOwn(parsed, "type") || typeof parsed.type !== "string") return fail("bad_field");
    const shape = Object.hasOwn(shapes, parsed.type) ? shapes[parsed.type] : undefined;
    if (!shape) return fail("unknown_type");
    const allowed = new Set<string>(["v", "type", ...shape.required, ...shape.optional]);
    // Object.keys lists own keys, including a JSON "__proto__" key, which is rejected here.
    if (!Object.keys(parsed).every((k) => allowed.has(k))) return fail("unknown_field");
    for (const field of shape.required) {
      if (!Object.hasOwn(parsed, field)) return fail("bad_field");
    }
    for (const field of [...shape.required, ...shape.optional]) {
      if (!Object.hasOwn(parsed, field)) continue;
      const code = checkField(field, parsed);
      if (code) return fail(code);
    }
    if (isInt(parsed.index, 0, LIMITS.maxInstances) && isInt(parsed.count, 1, LIMITS.maxInstances)) {
      if (parsed.index >= parsed.count) return fail("bad_field");
    }
    return { ok: true, message: parsed as T };
  } catch {
    return fail("bad_json");
  }
}

/** Validates a string received by the IDE from the page. Never throws. */
export function parsePageMessage(raw: unknown): ParseResult<PageMessage> {
  return parseWith<PageMessage>(raw, PAGE_SHAPES);
}

/** Validates a string received by the page from the IDE. Never throws. */
export function parseIdeMessage(raw: unknown): ParseResult<IdeMessage> {
  return parseWith<IdeMessage>(raw, IDE_SHAPES);
}

/**
 * Splits a `data-vi-src="path:offset"` attribute value. Paths contain no colon, so the only colon
 * separates the offset. Returns null for anything invalid.
 */
export function parseSrcAttribute(value: unknown): { path: string; offset: number } | null {
  if (typeof value !== "string" || value.length > LIMITS.maxPathChars + 12) return null;
  const match = /^([^:]+):(0|[1-9][0-9]{0,9})$/.exec(value);
  if (!match) return null;
  const offset = Number(match[2]);
  if (!isValidPath(match[1]) || offset > LIMITS.maxInt) return null;
  return { path: match[1], offset };
}

// Builders return the serialized message, or null when the content would not pass validation.
function build<T>(message: T, parse: (raw: unknown) => ParseResult<T>): string | null {
  const raw = JSON.stringify(message);
  return parse(raw).ok ? raw : null;
}

type Body<T> = T extends { type: infer _ } ? Omit<T, "v" | "type"> : never;

export function buildHello(body: Body<Extract<PageMessage, { type: "hello" }>> = {}): string | null {
  return build<PageMessage>({ v: 1, type: "hello", ...body }, parsePageMessage);
}
export function buildNavigate(body: Body<Extract<PageMessage, { type: "navigate" }>>): string | null {
  return build<PageMessage>({ v: 1, type: "navigate", ...body }, parsePageMessage);
}
export function buildUnmappable(body: Body<Extract<PageMessage, { type: "unmappable" }>>): string | null {
  return build<PageMessage>({ v: 1, type: "unmappable", ...body }, parsePageMessage);
}
export function buildHighlight(body: Body<Extract<IdeMessage, { type: "highlight" }>>): string | null {
  return build<IdeMessage>({ v: 1, type: "highlight", ...body }, parseIdeMessage);
}
export function buildClear(body: Body<Extract<IdeMessage, { type: "clear" }>> = {}): string | null {
  return build<IdeMessage>({ v: 1, type: "clear", ...body }, parseIdeMessage);
}
export function buildAck(body: Body<Extract<IdeMessage, { type: "ack" }>> = {}): string | null {
  return build<IdeMessage>({ v: 1, type: "ack", ...body }, parseIdeMessage);
}
export function buildError(body: Body<Extract<IdeMessage, { type: "error" }>>): string | null {
  return build<IdeMessage>({ v: 1, type: "error", ...body }, parseIdeMessage);
}

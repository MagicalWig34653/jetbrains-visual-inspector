# Bridge protocol v1

Status: normative for v1 (backlog item 4). Security background: ADR 0005, ADR 0009. Reference
implementation: `browser-extension/src/protocol.ts`. Shared test vectors (both sides must pass all of
them): `test-fixtures/protocol/vectors.json`.

The page is untrusted. The IDE validates every page message; the page also validates IDE messages
(defense in depth, same code).

## Envelope

One message = one JSON object in one string (the `JBCefJSQuery` request or response, or
`evaluateJavaScript` argument). Rules for every message:

- At most 4096 UTF-16 code units, checked before parsing.
- Top level is an object with `"v": 1` (integer) and `"type"` (string). Nothing else is added by the
  envelope.
- Closed schema: each type has fixed required and optional fields. Any other field, including
  `__proto__`, `constructor`, `prototype`, is rejected. No nested objects except `ancestor`.
- Integers are JSON integers (no fraction or exponent form that is not integral, no strings).
- Duplicate keys in one object are ambiguous and rejected as `bad_json` (the Kotlin side enforces this; `JSON.parse`
  keeps the last value, so the TypeScript side cannot see it). Integer-valued fractions or exponents (`1.0`,
  `1e0`) and `-0` are not integers: `v` gives `bad_version`, other integer fields `bad_field`.
- Receivers never throw on input. An invalid message is dropped; the receiver MAY answer with `error`.
  Nothing is repaired, truncated or defaulted.

## Page to IDE

| type | required | optional |
| --- | --- | --- |
| `hello` | | `id` |
| `navigate` | `path`, `offset` | `line`, `label`, `index`, `count`, `id` |
| `unmappable` | `tag` | `label`, `ancestor`, `id` |

- `hello`: sent once after load; announces v1. The IDE answers `ack` (or `error` `bad_version`).
- `navigate`: the user selected (clicked) an element. `path` and `offset` come from its
  `data-vi-src="path:offset"` attribute (split at the only colon; offset is a decimal integer without
  leading zeros). `offset` is a character offset into the file as the IDE served it. `line` is a hint
  only; the IDE uses `offset`. `label` is a display string such as `div.card`. `index` and `count`
  identify the instance among all elements sharing this location (`index < count`).
- `unmappable`: the selected element has no location. `tag` is the tag name. `ancestor` is the nearest
  ancestor that has a location (`path`, `offset`, optional `label`). The IDE may offer it, clearly
  labeled as an ancestor, never navigate to it silently (ADR 0009).
- Hover is local to the page overlay and not sent in v1. `select` is `navigate`.

## IDE to page

| type | required | optional |
| --- | --- | --- |
| `highlight` | `path`, `offset` | `index`, `id` |
| `clear` | | `id` |
| `ack` | | `ref` |
| `error` | `code` | `ref` |

- `highlight`: highlight all elements whose `data-vi-src` equals `path:offset`. `index` selects the
  instance to emphasize when cycling; absent means all equally.
- `clear`: remove all highlights.
- `ack` / `error`: reply to a message; `ref` is the `id` of the message answered. `code` is one of the
  error codes below.

## Field rules

| Field | Rule |
| --- | --- |
| `id`, `ref` | 1 to 64 chars of `A-Za-z0-9_.-` |
| `offset` | integer 0 to 2147483647 |
| `line` | integer 1 to 2147483647 |
| `index` | integer 0 to 9999; with `count`: `index < count` |
| `count` | integer 1 to 10000 |
| `label` | 1 to 128 chars, no control, format (bidi, zero width), separator or unpaired surrogate characters |
| `tag` | 1 to 32 chars, `[A-Za-z][A-Za-z0-9-]*` |
| `ancestor` | object with exactly `path`, `offset`, optional `label`, same rules |
| `code` | one of the error codes |

## Paths

Syntax rules, checked by both sides on every path (`path`, `ancestor.path`):

- Project-relative, `/` separated, 1 to 512 chars, each segment at most 255 chars.
- Rejected: absolute (leading `/`), empty segments (`//`, trailing `/`), `.` or `..` segments,
  backslash, colon (rejects schemes like `file:`, drive letters, `C:a`, NTFS streams), percent (rejects
  `%2e%2e`, `%2f`), `~` at segment start, segment ending in `.` or space or starting with space, any
  control, format, separator or unpaired surrogate character (includes NUL).
- Syntax checks are necessary, not sufficient. The IDE MUST then resolve the path against the project
  roots, canonicalize (symlinks, case, normalization) and require the result to stay inside the
  project and to exist, before opening anything. Failure gives `error` with `not_found` or `denied`.
  This step is IDE-only and not covered by the shared vectors.

## Error codes

| Code | Meaning |
| --- | --- |
| `bad_json` | not a string, not JSON, or not an object |
| `too_large` | over 4096 code units |
| `bad_version` | `v` missing or not 1 |
| `unknown_type` | `type` not in the direction's set |
| `unknown_field` | field outside the type's schema |
| `bad_field` | missing required field, wrong type or range |
| `bad_path` | path violates the path rules |
| `not_found` | IDE only: path does not resolve to a project file |
| `denied` | IDE only: path resolves outside the project, or action not allowed |

Check order (decides the code when several apply): size, JSON, version, type, unknown fields, required
fields, field values in schema order. `bad_path` is reported only when the field is a string.

## Versioning

A version other than 1 is rejected with `bad_version`. There is no negotiation in v1; a later version
adds a new `v` and a new spec section.

## Deliberately not in v1

No commands or process execution, no file reads or listings, no writes, no file content in any
message, no absolute paths, no arbitrary-URL fields, no batching, no ping/pong, no hover stream, no
settings. The only effects an accepted page message can cause are: open a project file in the editor
at an offset, show an unmappable notice, reply to the page.

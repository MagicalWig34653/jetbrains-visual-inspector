import assert from "node:assert/strict";
import { test } from "node:test";
import { PROTOCOL_VERSION } from "./protocol.ts";

// Toolchain smoke test: proves TypeScript sources run under the test runner.
test("protocol version is a non-negative integer", () => {
  assert.ok(Number.isInteger(PROTOCOL_VERSION) && PROTOCOL_VERSION >= 0);
});

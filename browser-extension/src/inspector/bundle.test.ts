import assert from "node:assert/strict";
import { test } from "node:test";
import vm from "node:vm";
// @ts-expect-error plain .mjs build script without types
import { buildInspector } from "../../scripts/build-inspector.mjs";

test("bundle is one plain script that defines only window.startInspector", () => {
  const code: string = buildInspector();
  assert.doesNotMatch(code, /^\s*(import|export)\s/m);
  const window: Record<string, unknown> = {};
  vm.runInNewContext(code, { window });
  assert.deepEqual(Object.keys(window), ["startInspector"]);
  assert.equal(typeof window.startInspector, "function");
});

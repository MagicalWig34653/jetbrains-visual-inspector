// Builds the inspector into ONE plain script (IIFE) without extra dependencies:
// node strips the TypeScript types, import/export syntax is removed, files are concatenated.
// Output: browser-extension/dist/inspector.js (git-ignored). Defines only `window.startInspector`.
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const src = resolve(dirname(fileURLToPath(import.meta.url)), "../src");
const files = ["protocol.ts", "inspector/core.ts", "inspector/inspector.ts"];

export function buildInspector() {
  const parts = files.map((file) => {
    const js = stripTypeScriptTypes(readFileSync(resolve(src, file), "utf8"), { mode: "strip" });
    return js
      .replace(/^import[\s\S]*?from\s+"[^"]+";\s*$/gm, "")
      .replace(/^export\s+(?=const|function|class|let)/gm, "")
      .replace(/^export\s*\{[^}]*\};?\s*$/gm, "");
  });
  return `(function () {\n"use strict";\n${parts.join("\n")}\nwindow.startInspector = startInspector;\n})();\n`;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const out = resolve(src, "../dist/inspector.js");
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, buildInspector());
  console.log(`wrote ${out}`);
}

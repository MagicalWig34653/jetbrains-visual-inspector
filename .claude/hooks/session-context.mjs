#!/usr/bin/env node
// SessionStart hook. Adds a short, read-only orientation from git and docs/current-state.md.
// It holds no state of its own: everything printed comes from versioned files and git.
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const projectDir = process.env.CLAUDE_PROJECT_DIR || process.cwd();

function git(...args) {
  try {
    return execFileSync("git", ["-C", projectDir, ...args], {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
      timeout: 3000,
    }).trim();
  } catch {
    return "";
  }
}

function nextTask() {
  try {
    const text = readFileSync(join(projectDir, "docs/current-state.md"), "utf8");
    const start = text.search(/^## Next task$/m);
    if (start === -1) return "";
    const section = text.slice(start).replace(/^## Next task\s*/, "");
    const end = section.search(/^## /m);
    return (end === -1 ? section : section.slice(0, end)).replace(/\s+/g, " ").trim().slice(0, 600);
  } catch {
    return "";
  }
}

const changed = git("status", "--porcelain").split("\n").filter(Boolean).length;
const lines = [
  "Sessions in this project are disposable; versioned files are the source of truth.",
  `Branch: ${git("branch", "--show-current") || "unknown"}. Uncommitted paths: ${changed}. Last commit: ${git("log", "-1", "--format=%h %s") || "none"}.`,
  `Next task per docs/current-state.md: ${nextTask() || "not recorded"}`,
  "Run /resume-project before starting substantial work.",
];

console.log(JSON.stringify({
  hookSpecificOutput: { hookEventName: "SessionStart", additionalContext: lines.join("\n") },
}));

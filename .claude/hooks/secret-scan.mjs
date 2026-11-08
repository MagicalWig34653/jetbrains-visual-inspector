#!/usr/bin/env node
// Deterministic secret and private-file scan. No dependencies.
//   node .claude/hooks/secret-scan.mjs --staged   scan the git index (used before commits)
//   node .claude/hooks/secret-scan.mjs --all      scan every tracked file (used by CI)
// Exit codes: 0 clean, 1 findings, 2 usage or git error.
import { execFileSync } from "node:child_process";
import { pathToFileURL } from "node:url";

const CONTENT_RULES = [
  ["GitHub token", /\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36,}\b/],
  ["GitHub fine-grained token", /\bgithub_pat_[A-Za-z0-9_]{50,}\b/],
  ["Anthropic API key", /\bsk-ant-[A-Za-z0-9_-]{20,}\b/],
  ["OpenAI-style API key", /\bsk-(?:proj-)?[A-Za-z0-9]{32,}\b/],
  ["AWS access key id", /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/],
  ["Google API key", /\bAIza[A-Za-z0-9_-]{35}\b/],
  ["Slack token", /\bxox[abprs]-[A-Za-z0-9-]{10,}\b/],
  ["npm token", /\bnpm_[A-Za-z0-9]{36}\b/],
  ["JetBrains Marketplace token", /\bperm:[A-Za-z0-9+/=_.-]{40,}/],
  ["Private key block", /-----BEGIN [A-Z ]*PRIVATE KEY-----/],
];

const FORBIDDEN_PATHS = [
  ["Environment file", /(^|\/)\.env(\.[^/]+)?$/],
  ["Key material", /\.(pem|key|p12|pfx|jks|keystore)$/i],
  ["SSH private key", /(^|\/)id_(rsa|dsa|ecdsa|ed25519)$/],
  ["Local Claude settings", /(^|\/)\.claude\/settings\.local\.json$/],
  ["Claude transcript or agent state", /(^|\/)\.claude\/(projects|agent-memory|agent-memory-local|worktrees)\//],
  ["Session transcript", /\.jsonl$/],
];

export function scanPath(path) {
  return FORBIDDEN_PATHS.filter(([, re]) => re.test(path)).map(([label]) => ({ path, label }));
}

export function scanContent(path, buffer) {
  if (buffer.includes(0)) return [];
  const findings = [];
  buffer.toString("utf8").split("\n").forEach((line, index) => {
    for (const [label, re] of CONTENT_RULES) {
      if (re.test(line)) findings.push({ path, line: index + 1, label });
    }
  });
  return findings;
}

function git(args, encoding) {
  return execFileSync("git", args, { encoding, maxBuffer: 64 * 1024 * 1024, stdio: ["ignore", "pipe", "pipe"] });
}

export function scanRepository(mode) {
  const nulSeparated =
    mode === "--staged"
      ? git(["diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z"], "utf8")
      : git(["ls-files", "-z"], "utf8");
  const paths = nulSeparated.split("\0").filter(Boolean);
  const findings = [];
  for (const path of paths) {
    findings.push(...scanPath(path));
    const blob = git(["show", `:${path}`], "buffer");
    findings.push(...scanContent(path, blob));
  }
  return { scanned: paths.length, findings };
}

function main() {
  const mode = process.argv[2];
  if (mode !== "--staged" && mode !== "--all") {
    console.error("usage: secret-scan.mjs --staged|--all");
    process.exit(2);
  }
  let result;
  try {
    result = scanRepository(mode);
  } catch (error) {
    console.error(`secret-scan: git failed: ${error.message}`);
    process.exit(2);
  }
  // Findings name the location and rule only, so the secret never reaches logs.
  for (const f of result.findings) {
    console.error(`${f.path}${f.line ? `:${f.line}` : ""}: ${f.label}`);
  }
  if (result.findings.length > 0) process.exit(1);
  console.log(`secret-scan: ${result.scanned} files clean`);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) main();

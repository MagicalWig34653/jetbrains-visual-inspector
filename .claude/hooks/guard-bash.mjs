#!/usr/bin/env node
// PreToolUse hook for Bash. Best-effort pattern guard: it catches accidents, it is not a sandbox.
// Policy is documented in docs/agent-workflow.md. Tests: .claude/hooks/test/.
import { realpathSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { resolve } from "node:path";
import { scanRepository } from "./secret-scan.mjs";

const DENY = [
  [/\brm\s+(-[a-zA-Z-]+\s+)*(\/|~|\$HOME|\$\{HOME\}|\.\.)(\/?\*?)(\s|;|&|\||$)/, "Recursive or broad delete of a root, home or parent directory."],
  [/\b(curl|wget)\b[^|;&]*\|\s*(sudo\s+)?(ba|z|da|fi)?sh\b/, "Piping a download into a shell runs unreviewed code."],
  [/\b(curl|wget)\b[^|;&]*\|\s*(sudo\s+)?(node|python3?|perl|ruby)\b/, "Piping a download into an interpreter runs unreviewed code."],
  [/\bnpm\s+(i|install|add|uninstall|rm|remove|link)\b[^;&|]*\s(-g|--global)\b/, "Global npm changes affect other projects."],
  [/\bclaude\s+(plugin|plugins|mcp)\s+(install|add|enable|marketplace)\b/, "Claude Code plugins and MCP servers must not be installed from this project."],
  [/(^|[\s;&|"'=>])(~|\$HOME|\$\{HOME\})\/\.claude\/(skills|agents|commands|hooks|plugins|settings\.json)/, "Global Claude configuration must not be touched; use .claude/ in this repository."],
  [/\bgh\s+release\s+(create|upload|edit|delete)\b/, "Releases are not published from this workflow."],
  [/\bgh\s+repo\s+(delete|archive|rename)\b/, "Destructive repository operations need a manual decision."],
  [/\bgh\s+repo\s+edit\b[^;&|]*--visibility\b/, "Changing repository visibility needs a manual decision."],
  [/\bpublishPlugin\b/, "JetBrains Marketplace publication is not part of this workflow."],
  [/\bgit\s+config\s+--(global|system)\b(?!\s+(--get|--list|-l)\b)/, "Global git configuration affects other projects."],
];

const ASK = [
  [/\bgit\s+reset\s+--hard\b/, "git reset --hard discards local work."],
  [/\bgit\s+clean\s+-[a-zA-Z]*f/, "git clean -f deletes untracked files."],
  [/\bgit\s+(checkout|restore)\s+(--\s+)?\.(\s|;|&|\||$)/, "This discards all uncommitted changes."],
  [/\bgit\s+branch\s+-D\b/, "Force-deleting a branch can lose commits."],
  [/\bgit\s+(commit|push)\b[^;&|]*--no-verify\b/, "--no-verify bypasses repository checks."],
  [/\bgit\s+add\b[^;&|]*\s(-f|--force)\b/, "Force-adding overrides .gitignore."],
  [/(^|[;&|]\s*)sudo\b/, "sudo acts outside the project."],
  [/\bchmod\s+(-R\s+)?0?777\b/, "chmod 777 makes files world-writable."],
];

const MUTATING = new Set(["rm", "mv", "cp", "ln", "chmod", "chown", "mkdir", "rmdir", "touch", "tee", "rsync", "dd"]);

function allowedRoots(projectDir) {
  const roots = [projectDir, "/tmp", "/private/tmp", "/dev", tmpdir()];
  for (const root of [...roots]) {
    try {
      roots.push(realpathSync(root));
    } catch {
      // Root does not exist on this platform.
    }
  }
  return roots;
}

function isOutside(path, roots) {
  const expanded = path.replace(/^(~|\$HOME|\$\{HOME\})(?=\/|$)/, homedir());
  if (!expanded.startsWith("/")) return false;
  const absolute = resolve(expanded);
  return !roots.some((root) => absolute === root || absolute.startsWith(`${root}/`));
}

function outsideWriteTarget(command, projectDir) {
  const roots = allowedRoots(projectDir);
  for (const segment of command.split(/&&|\|\||[;|\n]/)) {
    const tokens = segment.trim().split(/\s+/).map((t) => t.replace(/^['"]|['"]$/g, ""));
    const program = tokens[0]?.split("/").pop();
    if (program && MUTATING.has(program)) {
      const hit = tokens.slice(1).find((t) => !t.startsWith("-") && isOutside(t, roots));
      if (hit) return hit;
    }
    const redirect = segment.match(/>{1,2}\s*(['"]?)([^\s'";|&]+)\1/);
    if (redirect?.[2] && isOutside(redirect[2], roots)) return redirect[2];
  }
  return null;
}

function forcePushToProtectedBranch(command) {
  for (const segment of command.split(/&&|\|\||[;\n]/)) {
    if (!/\bgit\s+push\b/.test(segment)) continue;
    if (!/(\s--force(-with-lease|-if-includes)?\b|\s-[a-zA-Z]*f\b|\s\+\S+)/.test(segment)) continue;
    const words = segment.trim().split(/\s+/).slice(segment.trim().split(/\s+/).indexOf("push") + 1);
    const refs = words.filter((w) => !w.startsWith("-")).slice(1);
    if (refs.length === 0 || refs.some((r) => /(^|[:+/])(main|HEAD)$/.test(r))) return true;
  }
  return false;
}

export function decide(command, projectDir, scanStaged = () => scanRepository("--staged")) {
  if (forcePushToProtectedBranch(command)) {
    return { decision: "deny", reason: "Force-pushing to main is forbidden. Name an explicit feature branch if a force-push is really needed." };
  }
  for (const [pattern, reason] of DENY) {
    if (pattern.test(command)) return { decision: "deny", reason };
  }
  if (/\bgit\s+(-C\s+\S+\s+)?commit\b/.test(command)) {
    try {
      const { findings } = scanStaged();
      if (findings.length > 0) {
        const list = findings.slice(0, 10).map((f) => `${f.path}${f.line ? `:${f.line}` : ""} (${f.label})`).join("; ");
        return { decision: "deny", reason: `Secret scan blocked this commit: ${list}` };
      }
    } catch {
      // Not a git repository or nothing to scan; git itself will report the problem.
    }
  }
  for (const [pattern, reason] of ASK) {
    if (pattern.test(command)) return { decision: "ask", reason };
  }
  const outside = outsideWriteTarget(command, projectDir);
  if (outside) return { decision: "ask", reason: `This command writes outside the project: ${outside}` };
  return null;
}

async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  return Buffer.concat(chunks).toString("utf8");
}

if (process.argv[1] && process.argv[1].endsWith("guard-bash.mjs")) {
  const input = JSON.parse(await readStdin());
  const projectDir = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
  const result = decide(String(input.tool_input?.command ?? ""), projectDir);
  if (result) {
    console.log(JSON.stringify({
      hookSpecificOutput: {
        hookEventName: "PreToolUse",
        permissionDecision: result.decision,
        permissionDecisionReason: result.reason,
      },
    }));
  }
}

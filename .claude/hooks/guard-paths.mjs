#!/usr/bin/env node
// PreToolUse hook for Read, Write, Edit and NotebookEdit.
// Blocks credential files and global Claude configuration; asks before writes outside the project.
import { realpathSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { resolve } from "node:path";

const SENSITIVE = [
  /(^|\/)\.env(\.[^/]+)?$/,
  /\.(pem|key|p12|pfx|jks|keystore)$/i,
  /(^|\/)id_(rsa|dsa|ecdsa|ed25519)$/,
];

function underHome(path, home, entries) {
  return entries.some((e) => path === `${home}/${e}` || path.startsWith(`${home}/${e}/`));
}

export function decide(toolName, filePath, projectDir, home = homedir()) {
  if (!filePath) return null;
  const absolute = resolve(projectDir, filePath.replace(/^~(?=\/|$)/, home));

  if (SENSITIVE.some((re) => re.test(absolute)) || underHome(absolute, home, [".ssh", ".aws", ".gnupg", ".netrc", ".config/gh"])) {
    return { decision: "deny", reason: `Access to credential files is blocked: ${absolute}` };
  }
  if (toolName === "Read") return null;

  const globalClaude = ["skills", "agents", "commands", "hooks", "plugins", "settings.json"].map((e) => `.claude/${e}`);
  if (underHome(absolute, home, globalClaude)) {
    return { decision: "deny", reason: "Global Claude configuration must not be changed; use .claude/ in this repository." };
  }

  const roots = [projectDir, "/tmp", "/private/tmp", tmpdir()];
  for (const root of [...roots]) {
    try {
      roots.push(realpathSync(root));
    } catch {
      // Root does not exist on this platform.
    }
  }
  if (!roots.some((root) => absolute === root || absolute.startsWith(`${root}/`))) {
    return { decision: "ask", reason: `This edit is outside the project: ${absolute}` };
  }
  return null;
}

async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  return Buffer.concat(chunks).toString("utf8");
}

if (process.argv[1] && process.argv[1].endsWith("guard-paths.mjs")) {
  const input = JSON.parse(await readStdin());
  const projectDir = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
  const filePath = input.tool_input?.file_path ?? input.tool_input?.notebook_path;
  const result = decide(String(input.tool_name ?? ""), filePath ? String(filePath) : "", projectDir);
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

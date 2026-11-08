#!/usr/bin/env node
// Validates the project's agent setup and required documentation. No dependencies.
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const errors = [];
const read = (path) => readFileSync(join(root, path), "utf8");

function frontmatter(text) {
  const match = text.match(/^---\n([\s\S]*?)\n---\n/);
  if (!match) return null;
  return Object.fromEntries(
    match[1].split("\n").map((line) => line.match(/^([A-Za-z_-]+):\s*(.*)$/)).filter(Boolean).map((m) => [m[1], m[2]]),
  );
}

const requiredDocs = [
  "CLAUDE.md", "README.md", "LICENSE", "THIRD-PARTY-NOTICES.md",
  "docs/architecture.md", "docs/roadmap.md", "docs/current-state.md", "docs/backlog.md",
  "docs/development.md", "docs/agent-workflow.md", "docs/skills.md", "docs/decisions/README.md",
];
for (const path of requiredDocs) {
  if (!existsSync(join(root, path))) errors.push(`missing required file: ${path}`);
}

const currentState = existsSync(join(root, "docs/current-state.md")) ? read("docs/current-state.md") : "";
for (const heading of ["## Implemented", "## Verification status", "## Next task"]) {
  if (!currentState.includes(heading)) errors.push(`docs/current-state.md lacks "${heading}"`);
}

const skillsDoc = existsSync(join(root, "docs/skills.md")) ? read("docs/skills.md") : "";
for (const entry of readdirSync(join(root, ".claude/skills"), { withFileTypes: true })) {
  if (!entry.isDirectory()) continue;
  const path = `.claude/skills/${entry.name}/SKILL.md`;
  if (!existsSync(join(root, path))) {
    errors.push(`missing ${path}`);
    continue;
  }
  const meta = frontmatter(read(path));
  if (!meta?.description) errors.push(`${path}: frontmatter needs a description`);
  if (meta?.name && meta.name !== entry.name) errors.push(`${path}: name differs from directory`);
  if (!skillsDoc.includes(`\`${entry.name}\``)) errors.push(`docs/skills.md does not list ${entry.name}`);
}

for (const file of readdirSync(join(root, ".claude/agents")).filter((f) => f.endsWith(".md"))) {
  const path = `.claude/agents/${file}`;
  const meta = frontmatter(read(path));
  if (!meta?.name || !meta?.description) errors.push(`${path}: frontmatter needs name and description`);
  if (meta && "memory" in meta) errors.push(`${path}: persistent agent memory is forbidden (ADR 0001)`);
}

const settings = JSON.parse(read(".claude/settings.json"));
for (const rule of settings.permissions?.allow ?? []) {
  if (rule === "Bash" || /^Bash\(\s*\*?\s*(:\*)?\s*\)$/.test(rule)) errors.push(`.claude/settings.json: blanket shell rule "${rule}"`);
}
if (settings.permissions?.defaultMode === "bypassPermissions") errors.push(".claude/settings.json: bypassPermissions is forbidden");

if (errors.length > 0) {
  for (const error of errors) console.error(`check-repo: ${error}`);
  process.exit(1);
}
console.log("check-repo: ok");

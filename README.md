# JetBrains Visual Inspector

An open-source plugin for WebStorm and PhpStorm, in development, that aims to connect rendered web
pages and their source code in both directions, inspired by Dreamweaver's Live View and Inspect
Mode.

This is an independent community project. It is not affiliated with or endorsed by JetBrains. The
plugin's display name is **Visual Inspector**.

> **Status: project setup only.** The plugin builds and installs, but it only shows a placeholder
> tool window. **None of the features below are implemented yet.**

## Planned features

- Click an element in a preview and jump to its exact source location.
- Highlight elements under the cursor.
- Select code in the editor and highlight the rendered element.
- Integrated browser preview based on JCEF.
- Chrome and Firefox connection through a browser extension.
- Static HTML first, later React, Vue and Svelte, and PHP templates, through modular adapters.
- Secure browser-IDE communication that treats every page as untrusted.

Not planned: visual page editing, a website builder, cloud services or user accounts. The plugin
will never modify your code.

## Implementation status

| Area | Status |
| --- | --- |
| Plugin scaffold with placeholder tool window | Done |
| Build, test and verification tooling | Done |
| JCEF preview | Not implemented |
| DOM inspector and highlighting | Not implemented |
| Browser-to-source and source-to-browser navigation | Not implemented |
| Browser-IDE bridge | Not implemented |
| Chrome and Firefox extension | Not implemented |
| Framework adapters | Not implemented |

Details: [`docs/current-state.md`](docs/current-state.md) and [`docs/roadmap.md`](docs/roadmap.md).

## Architecture

```text
JetBrains IDE: Kotlin plugin              Web page: TypeScript
  JCEF preview                  bridge      DOM inspector
  Bridge endpoint, validation  <------->    Framework adapter
  Source resolver, navigation               (DOM element -> source location)
```

The design is preliminary until the feasibility phase is finished. See
[`docs/architecture.md`](docs/architecture.md) and [`docs/decisions/`](docs/decisions/).

## Repository layout

| Path | Content |
| --- | --- |
| `plugin/` | IntelliJ Platform plugin (Kotlin, Gradle) |
| `browser-extension/` | Browser extension and DOM inspector (TypeScript, scaffold) |
| `framework-adapters/` | Framework adapters (none yet) |
| `test-fixtures/` | Sample projects for tests |
| `docs/` | Architecture, decisions, roadmap, status |
| `.claude/` | Project-local Claude Code agents, skills and hooks |

## Development

Requires a JDK (21 or newer) to run Gradle, and Node.js 24 or newer. There is no release yet.

```bash
git clone https://github.com/MagicalWig34653/jetbrains-visual-inspector.git
cd jetbrains-visual-inspector
npm ci
./scripts/verify.sh           # all checks
cd plugin && ./gradlew runIde # start a sandbox WebStorm with the plugin
```

More in [`docs/development.md`](docs/development.md). The first Gradle run downloads an IDE and
needs several gigabytes.

## License

[MIT](LICENSE). Some files are under other licenses; see
[`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md).

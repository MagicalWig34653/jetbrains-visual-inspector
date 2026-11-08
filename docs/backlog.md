# Backlog

Ordered by priority. Move finished items out via `/update-project-state`; history stays in git.

## Now: Phase 1

1. **Feasibility analysis and architecture discussion.** Work through section 10 of
   `projektauftrag.md`: review current APIs; research open-source browser-to-source tools; explain
   mapping techniques and limits; compare plugin-only with plugin plus browser adapter; rate
   framework effort; identify risks and prototypes; produce a prioritized plan. Separate proven
   APIs, plausible approaches and experiments. **End by asking the project owner targeted
   architecture questions; do not start implementation first.**
   The owner's product decisions in ADR 0008 are fixed inputs. Also answer: can the IDE's built-in
   web server rewrite responses in memory through a supported API; how can `plugin.xml` restrict
   installation to WebStorm and PhpStorm; how can vanilla PHP output be mapped to source.
   Result: `docs/research/feasibility.md`, updated ADR 0004 and 0005.
2. **Spike: in-memory location injection** for static HTML, first via the IDE's built-in web
   server, otherwise via plugin-served content to JCEF (no writes to user files). Result: measured
   feasibility recorded in ADR 0004.
3. **Spike: JCEF availability and bridge round trip** with `JBCefJSQuery` in WebStorm and PhpStorm,
   including behavior when JCEF is unsupported.
4. **Define bridge protocol v1** with a validation test suite on both sides.

## Next: Phase 2

5. JCEF preview tool window replacing the placeholder.
6. Project-confined source location validator with path traversal and symlink tests.
7. Inspector overlay: hover highlight, tooltip, click capture.
8. Click-to-source navigation for static HTML, with feedback for unmappable elements.
9. Editor-to-preview highlighting, debounced and configurable.

## Later

- Product scope: `plugin.xml` currently depends only on `com.intellij.modules.platform`, so it
  installs in any IDE. Decide in phase 1 whether to require WebStorm/PhpStorm features (for example
  the bundled `JavaScript` plugin) or HTML support explicitly. The PSI smoke test relies on the
  HTML/XML support bundled in WebStorm.

- Adapter interface and React, Vue, Svelte adapters.
- Chrome and Firefox extension with an authenticated local channel.
- Twig and Blade evaluation.
- CI: browser-extension build once a manifest exists.
- CI: dependency audit once runtime dependencies exist. Dependabot is active already.
- Expand the Plugin Verifier matrix to all supported product and version combinations.
- Plugin icon, and a trademark review of the product name before any Marketplace submission.
- Migrate the plugin to a newer platform baseline as JetBrains retires old branches.

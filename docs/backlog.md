# Backlog

Ordered by priority. Move finished items out via `/update-project-state`; history stays in git.

## Now: Phase 1

1. **Close phase 1 analysis.** The analysis (`docs/research/feasibility.md`) and the owner answers
   (ADR 0009) exist. ADR 0004 and 0005 carry the owner decisions and stay `Proposed`: 0004 until
   spike 2 has a result, 0005 until bridge protocol v1 (item 4) is tested. Nothing else remains.
2. **Gate the location injection handler** (spike 2 is done, ADR 0004): `LocationInjectingFileHandler`
   currently rewrites every HTML page of every project on the built-in server. Restrict it to
   previews opened by the Visual Inspector (for example a per-preview token or path) before release.
   Also test an untrusted project and a real browser load.
3. **Verify the JCEF preview by hand** (spike 3 is coded and compiled, ADR 0004): run `runIde` in
   WebStorm and PhpStorm and check real rendering, the "received Ping" round trip, the navigation
   and popup veto and the "Built-in server" button. Headless tests cannot cover JCEF.

## Next: Phase 2

5. JCEF preview tool window replacing the placeholder.
6. ~~Project-confined source location validator~~ done with protocol v1 (`ProjectPathResolver`); wire it into navigation in item 8.
7. Inspector overlay: hover highlight, tooltip, click capture.
8. Click-to-source navigation for static HTML, with feedback for unmappable elements.
9. Editor-to-preview highlighting, debounced and configurable.
10. **Spike: vanilla PHP mapping** at the end of phase 2. Test an optional `auto_prepend_file`
    script and a plugin-side proxy with structural matching against the `.php` HTML; only HTML
    elements need a location (ADR 0009). Result decides the PHP mechanism for phase 3.

## Later

- Restrict `plugin.xml` to WebStorm and PhpStorm (ADR 0008) once phase 1 has found the supported
  way. It currently depends only on `com.intellij.modules.platform`.
- Adapter interface; vanilla PHP first, then Vue, Svelte and React adapters (ADR 0008).
- Chrome and Firefox extension with an authenticated local channel.
- Twig and Blade evaluation.
- CI: browser-extension build once a manifest exists.
- CI: dependency audit once runtime dependencies exist. Dependabot is active already.
- Expand the Plugin Verifier matrix to all supported product and version combinations.
- Plugin icon, and a trademark review of the product name before any Marketplace submission.
- Migrate the plugin to a newer platform baseline as JetBrains retires old branches.

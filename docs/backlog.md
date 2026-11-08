# Backlog

Ordered by priority. Move finished items out via `/update-project-state`; history stays in git.

## Now: Phase 1

1. **Close phase 1 analysis.** The analysis (`docs/research/feasibility.md`) and the owner answers
   (ADR 0009) exist. ADR 0004 and 0005 carry the owner decisions and stay `Proposed`: 0004 until
   spike 2 has a result, 0005 until bridge protocol v1 (item 4) is tested. Nothing else remains.
2. **Spike: in-memory location injection** for static HTML via the IDE's built-in web server only
   (ADR 0009). If it fails, ask the owner before building the plugin-served fallback. Result:
   measured feasibility recorded in ADR 0004.
3. **Spike: JCEF availability and bridge round trip** with `JBCefJSQuery` in WebStorm and PhpStorm,
   including behavior when JCEF is unsupported. Add the `com.intellij.modules.jcef` dependency and
   the runtime product guard (WebStorm and PhpStorm only, ADR 0009).
4. **Define bridge protocol v1** with a validation test suite on both sides.

## Next: Phase 2

5. JCEF preview tool window replacing the placeholder.
6. Project-confined source location validator with path traversal and symlink tests.
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

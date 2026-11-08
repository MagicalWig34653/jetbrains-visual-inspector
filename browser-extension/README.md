# Browser extension

Planned Chrome and Firefox extension and the shared DOM inspector.

**Status:** toolchain scaffold only. There is no manifest, no content script and no inspector yet,
so there is nothing to load into a browser. See [`docs/roadmap.md`](../docs/roadmap.md).

## Inspector overlay

`src/inspector/` (logic in `core.ts`, DOM layer in `inspector.ts`). Build one plain script with
`npm run build:inspector -w browser-extension`; output `browser-extension/dist/inspector.js`
(git-ignored, never committed). It defines only `window.startInspector(transport)`; the host
supplies `transport = { send(raw), subscribe?(receive) }`. Alt+click toggles inspect mode, plain
click then sends `navigate` or `unmappable`. The returned handle offers `setInspectMode`, `receive`
and `dispose`.

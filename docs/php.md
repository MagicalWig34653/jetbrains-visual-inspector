# Vanilla PHP in the Visual Inspector preview

Development only. Never enable this on a production or shared server: it rewrites the HTML your PHP files
produce and the page can ask the IDE to open project files.

## 1. Enable the prepend script

`php-support/visual-inspector-prepend.php` adds `data-vi-src="path/file.php:OFFSET"` to HTML start tags that
are written as plain markup in `.php` files (outside `<?php ?>`). It never writes files.

```bash
VISUAL_INSPECTOR_DEV=1 php -d auto_prepend_file=/abs/path/to/visual-inspector-prepend.php -S localhost:8000
```

- `VISUAL_INSPECTOR_DEV=1` (or ini `visual_inspector.dev=1`) switches it on; without it the script does nothing.
- `VISUAL_INSPECTOR_ROOT` (or `visual_inspector.root`) is the project root; default is the document root.
  Set it when the IDE project root is above the document root. Attribute paths are relative to this root and
  must equal the paths relative to the IDE project root, so open the folder that contains the PHP root.
- `vendor/`, `node_modules/` and files outside the root are not rewritten.

MAMP or Apache: set `auto_prepend_file` in the `php.ini` of that PHP version and the variable in the
environment of the server. Restart the server; with opcache, reset it after changing settings.

## 2. Docker example

Run from the project root (the folder open in the IDE, containing `php-support/`):

```bash
docker run --rm -p 8000:8000 -v "$PWD":/app:ro -w /app/test-fixtures/php-site \
  -e VISUAL_INSPECTOR_DEV=1 -e VISUAL_INSPECTOR_ROOT=/app \
  php:8.5-cli php -d auto_prepend_file=/app/php-support/visual-inspector-prepend.php -S 0.0.0.0:8000
```

`0.0.0.0` is inside the container; Docker publishes it on your machine. For the preview use
`http://localhost:8000/` (and publish with `-p 127.0.0.1:8000:8000` to keep it off the network).

## 3. Use in the IDE

1. Run "Open PHP Page in Visual Inspector" (Tools menu or editor context menu), type the page URL, press Enter.
2. The page loads without the inspector. Press "Trust this origin" and confirm once; the page reloads with
   the overlay. Trust is per exact `scheme://host:port`, stored per project, and revocable with the same
   button. The plugin never starts servers.
3. Click an element to open its source. In a `.php` file, "Highlight in Visual Inspector Preview" works when
   the page contains elements from that file (matched by the attribute paths, not the URL).

## Limits (Spike 5, ADR 0004)

- Only static markup in `.php` files is mapped. HTML from `echo`, heredocs, `printf` and template engines
  (Twig, Blade, compiled or cached templates) has no location; the inspector offers the nearest mapped
  ancestor, labeled. Tag names split by PHP get none.
- Offsets refer to the file on disk at render time. Unsaved editor text or later edits give stale offsets
  until the page is reloaded. A UTF-8 BOM may shift offsets by one (unverified).
- Opcache keeps rewritten code: restart or reset it after changing the setting.
- The response HTML changes: pages that hash their own output, or AJAX fragments built from inline HTML, may break.
- Tested only with PHP 8.5 CLI server; php-fpm, Apache and older PHP are untested.

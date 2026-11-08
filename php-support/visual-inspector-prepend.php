<?php
/**
 * Visual Inspector, optional dev-only PHP support (spike). Self-contained, no dependencies.
 *
 * Use:  php -d auto_prepend_file=/path/to/visual-inspector-prepend.php ...   with  VISUAL_INSPECTOR_DEV=1
 *       (or ini value visual_inspector.dev=1). Without it this file does nothing.
 *
 * What it does: registers a user-space `file://` stream wrapper. When PHP includes a `.php` file that lives
 * inside the project root (not vendor/, not node_modules/, not outside the root), the source is tokenized with
 * token_get_all() and every HTML start tag in a T_INLINE_HTML token (the markup outside <?php ?>) gets
 *   data-vi-src="relative/path.php:OFFSET"
 * OFFSET is the UTF-16 index of the tag's `<` in the file with line breaks normalized to "\n". That is the
 * same meaning as the attribute on static HTML (plugin LocationInjector, IDE Document offset). The result is
 * compiled in memory. This script never writes, renames or deletes anything; line numbers do not change.
 *
 * Limits: markup built in PHP strings, heredocs or by templates is not touched (no attribute, by design).
 * Tag names split by PHP code (`<d<?= 'iv' ?>`) get no attribute. Any error in rewriting falls back to the
 * original source.
 *
 * Settings (env var or ini, env wins): VISUAL_INSPECTOR_DEV / visual_inspector.dev = 1,
 *   VISUAL_INSPECTOR_ROOT / visual_inspector.root = project root (default: document root).
 */
declare(strict_types=1);

namespace VisualInspector;

/** Adds the attribute to start tags found in inline HTML chunks; state carries across PHP blocks. */
final class Rewriter
{
    private const RAW_TEXT = ['script', 'style', 'textarea', 'title'];
    private const WS = " \t\n\r\f\v";

    private string $mode = 'text';   // text | tag | raw | comment | decl
    private ?string $quote = null;   // open attribute quote while in a tag
    private ?string $rawName = null; // raw-text element whose start tag is being read, or whose body we are in
    private bool $rawPending = false;
    private int $curByte = 0;        // offset cache: byte position ...
    private int $curUnits = 0;       // ... and its UTF-16 index

    private function __construct(private string $src, private string $attrPath) {}

    public static function rewrite(string $src, string $relPath): string
    {
        $self = new self($src, strtr($relPath, ['&' => '&amp;', '"' => '&quot;', '<' => '&lt;', '>' => '&gt;']));
        $out = '';
        $byte = 0;
        foreach (token_get_all($src) as $token) {
            $text = is_array($token) ? $token[1] : $token;
            $out .= (is_array($token) && $token[0] === \T_INLINE_HTML) ? $self->chunk($text, $byte) : $text;
            $byte += strlen($text);
        }
        return $out;
    }

    /** UTF-16 index of byte offset $byte (monotonic calls only); "\r\n" and lone "\r" count as one unit. */
    private function units(int $byte): int
    {
        $part = substr($this->src, $this->curByte, $byte - $this->curByte);
        $n = preg_match_all('/[^\x80-\xBF]/', $part) + preg_match_all('/[\xF0-\xF7]/', $part)
            - preg_match_all('/\r\n/', $part);
        if ($part !== '' && $part[0] === "\n" && $this->curByte > 0 && $this->src[$this->curByte - 1] === "\r") {
            $n--;
        }
        $this->curByte = $byte;
        return $this->curUnits += $n;
    }

    private function chunk(string $t, int $base): string
    {
        $n = strlen($t);
        $out = '';
        $copied = 0;
        $i = 0;
        while ($i < $n) {
            switch ($this->mode) {
                case 'comment':
                    $p = strpos($t, '-->', $i);
                    [$i, $this->mode] = $p === false ? [$n, 'comment'] : [$p + 3, 'text'];
                    break;
                case 'decl':
                    $p = strpos($t, '>', $i);
                    [$i, $this->mode] = $p === false ? [$n, 'decl'] : [$p + 1, 'text'];
                    break;
                case 'raw':
                    $re = '~</' . preg_quote((string)$this->rawName, '~') . '(?=[\s/>])~i';
                    if (preg_match($re, $t, $m, \PREG_OFFSET_CAPTURE, $i) === 1) {
                        $i = $m[0][1];
                        $this->mode = 'text';
                    } else {
                        $i = $n;
                    }
                    break;
                case 'tag':
                    $i = $this->inTag($t, $i, $n);
                    break;
                default: // text
                    $lt = strpos($t, '<', $i);
                    if ($lt === false) {
                        break 2;
                    }
                    $i = $lt + 1;
                    if (substr_compare($t, '<!--', $lt, 4) === 0) {
                        $this->mode = 'comment';
                        $i = $lt + 4;
                    } elseif (substr_compare($t, '<![CDATA[', $lt, 9) === 0) {
                        $p = strpos($t, ']]>', $lt + 9);
                        $i = $p === false ? $n : $p + 3;
                    } elseif ($lt + 1 < $n && strpos('!?/', $t[$lt + 1]) !== false) {
                        $this->mode = 'decl';
                        $i = $lt + 2;
                    } elseif ($lt + 1 < $n && ctype_alpha($t[$lt + 1])) {
                        $nameEnd = $lt + 1 + strcspn($t, self::WS . '/>', $lt + 1);
                        $this->mode = 'tag';
                        $this->quote = null;
                        $this->rawName = strtolower(substr($t, $lt + 1, $nameEnd - $lt - 1));
                        $this->rawPending = in_array($this->rawName, self::RAW_TEXT, true);
                        if ($nameEnd < $n) { // the name is complete inside this chunk
                            $out .= substr($t, $copied, $nameEnd - $copied)
                                . ' data-vi-src="' . $this->attrPath . ':' . $this->units($base + $lt) . '"';
                            $copied = $nameEnd;
                        }
                        $i = $nameEnd;
                    }
            }
        }
        return $out . substr($t, $copied);
    }

    /** Scans inside a start tag; returns the next index. Quotes count only where an attribute value starts. */
    private function inTag(string $t, int $i, int $n): int
    {
        while ($i < $n) {
            if ($this->quote !== null) {
                $p = strpos($t, $this->quote, $i);
                if ($p === false) {
                    return $n;
                }
                $this->quote = null;
                $i = $p + 1;
                continue;
            }
            $c = $t[$i];
            if ($c === '>') {
                $selfClosing = $i > 0 && $t[$i - 1] === '/';
                $this->mode = ($this->rawPending && !$selfClosing) ? 'raw' : 'text';
                return $i + 1;
            }
            if ($c === '=') {
                $j = $i + 1;
                $j += strspn($t, self::WS, $j);
                if ($j < $n && ($t[$j] === '"' || $t[$j] === "'")) {
                    $this->quote = $t[$j];
                    $i = $j + 1;
                    continue;
                }
            }
            $i++;
        }
        return $n;
    }
}

/** The `file://` wrapper: rewrites eligible includes, passes everything else to the native wrapper. */
final class Stream
{
    public $context;
    public static string $root = '';
    private const MAX_BYTES = 2_000_000;
    // PHP source value of STREAM_OPEN_FOR_INCLUDE; the constant is documented but not defined in userland.
    private const OPEN_FOR_INCLUDE = 0x80;
    private $handle;
    private ?string $buf = null;
    private int $pos = 0;

    public static function register(): void
    {
        stream_wrapper_unregister('file');
        stream_wrapper_register('file', self::class);
    }

    /** Runs $f with the native file wrapper active, then puts ours back. */
    private static function native(callable $f)
    {
        stream_wrapper_restore('file');
        try {
            return $f();
        } finally {
            self::register();
        }
    }

    /** Project-relative path when $path is a .php file inside the root and outside vendor/node_modules. */
    private static function eligible(string $path): ?string
    {
        if (strtolower(pathinfo($path, \PATHINFO_EXTENSION)) !== 'php') {
            return null;
        }
        $real = self::native(fn() => realpath($path));
        if ($real === false || !str_starts_with($real, self::$root . \DIRECTORY_SEPARATOR)) {
            return null;
        }
        $rel = str_replace(\DIRECTORY_SEPARATOR, '/', substr($real, strlen(self::$root) + 1));
        return preg_match('~(^|/)(vendor|node_modules)/~', $rel) === 1 ? null : $rel;
    }

    public function stream_open(string $path, string $mode, int $options, ?string &$opened): bool
    {
        $rel = ($options & self::OPEN_FOR_INCLUDE) && $mode === 'rb' ? self::eligible($path) : null;
        $this->handle = self::native(fn() => $this->context !== null
            ? @fopen($path, $mode, false, $this->context) : @fopen($path, $mode));
        if ($this->handle === false) {
            return false;
        }
        if ($rel !== null) {
            $src = stream_get_contents($this->handle, self::MAX_BYTES + 1);
            if ($src !== false && strlen($src) <= self::MAX_BYTES) {
                try {
                    $src = Rewriter::rewrite($src, $rel);
                } catch (\Throwable) {
                    // keep the original source
                }
            }
            $this->buf = $src === false ? '' : $src;
        }
        return true;
    }

    public function stream_read(int $count): string|false
    {
        if ($this->buf === null) {
            return fread($this->handle, $count);
        }
        $chunk = substr($this->buf, $this->pos, $count);
        $this->pos += strlen($chunk);
        return $chunk;
    }

    public function stream_eof(): bool
    {
        return $this->buf === null ? feof($this->handle) : $this->pos >= strlen($this->buf);
    }

    public function stream_stat(): array|false
    {
        $s = fstat($this->handle);
        if ($s !== false && $this->buf !== null) {
            $s['size'] = $s[7] = strlen($this->buf);
        }
        return $s;
    }

    public function stream_close(): void { fclose($this->handle); }
    public function stream_set_option(int $option, int $arg1, ?int $arg2): bool { return false; }
    public function stream_tell(): int { return $this->buf === null ? ftell($this->handle) : $this->pos; }
    public function stream_write(string $data): int { return $this->buf === null ? (int)fwrite($this->handle, $data) : 0; }
    public function stream_flush(): bool { return $this->buf === null && fflush($this->handle); }
    public function stream_lock(int $op): bool { return $this->buf === null && flock($this->handle, $op); }

    public function stream_seek(int $offset, int $whence): bool
    {
        if ($this->buf === null) {
            return fseek($this->handle, $offset, $whence) === 0;
        }
        $p = match ($whence) { \SEEK_SET => $offset, \SEEK_CUR => $this->pos + $offset, default => strlen($this->buf) + $offset };
        if ($p < 0) {
            return false;
        }
        $this->pos = $p;
        return true;
    }

    // Everything below only forwards the application's own calls to the native wrapper.
    public function url_stat(string $path, int $flags): array|false
    {
        return self::native(fn() => $flags & \STREAM_URL_STAT_QUIET ? @stat($path) : stat($path));
    }
    public function unlink(string $path): bool { return self::native(fn() => unlink($path)); }
    public function rename(string $from, string $to): bool { return self::native(fn() => rename($from, $to)); }
    public function mkdir(string $path, int $mode, int $options): bool
    {
        return self::native(fn() => mkdir($path, $mode, (bool)($options & \STREAM_MKDIR_RECURSIVE)));
    }
    public function rmdir(string $path, int $options): bool { return self::native(fn() => rmdir($path)); }
    public function dir_opendir(string $path, int $options): bool
    {
        $this->handle = self::native(fn() => @opendir($path));
        return $this->handle !== false;
    }
    public function dir_readdir(): string|false { return readdir($this->handle); }
    public function dir_rewinddir(): bool { rewinddir($this->handle); return true; }
    public function dir_closedir(): bool { closedir($this->handle); return true; }
}

(static function (): void {
    $get = static function (string $env, string $ini): string {
        $v = getenv($env);
        return $v !== false && $v !== '' ? $v : (string)ini_get($ini);
    };
    if ($get('VISUAL_INSPECTOR_DEV', 'visual_inspector.dev') !== '1') {
        return; // disabled by default
    }
    $root = $get('VISUAL_INSPECTOR_ROOT', 'visual_inspector.root');
    $root = realpath($root !== '' ? $root : (string)($_SERVER['DOCUMENT_ROOT'] ?? ''));
    if ($root === false || $root === '' || $root === \DIRECTORY_SEPARATOR || !function_exists('token_get_all')) {
        return;
    }
    Stream::$root = rtrim($root, \DIRECTORY_SEPARATOR);
    Stream::register();
})();

<?php
// Reports opcache state so the check can tell whether a second request ran from cached code.
$s = function_exists('opcache_get_status') ? opcache_get_status(true) : false;
echo json_encode([
    'enabled' => $s !== false && $s['opcache_enabled'],
    'cached' => $s ? array_map('basename', array_keys($s['scripts'] ?? [])) : [],
    'hits' => $s['opcache_statistics']['hits'] ?? null,
]);

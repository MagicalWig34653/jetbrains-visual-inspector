<?php $items = ['a', 'b', 'c']; $title = 'Page'; ?>
<!DOCTYPE html>
<html lang="en">
<head><meta charset="utf-8"><title>Fixture <?= $title ?></title><!-- <b id="in-comment"> --></head>
<body>
<?php include __DIR__ . '/partials/header.php'; ?>
<main id="main">
  <h1 id="unicode">Ünïcode 😀 heading</h1>
  <ul id="loop">
  <?php foreach ($items as $i): ?>
    <li class="loop-item"><span class="loop-label"><?= $i ?></span></li>
  <?php endforeach; ?>
  </ul>
  <ul id="partial-loop">
  <?php foreach ($items as $i) { include 'partials/card.php'; } ?>
  </ul>
  <ul id="echoed">
  <?php foreach ($items as $i) { echo "<li class=\"echoed-item\">$i</li>"; } ?>
  </ul>
  <div id="split" <?php echo 'class="dyn"'; ?>>split content</div>
  <a id="quoted" href="<?= '/x?a=1&b=2' ?>" title="a > b">dynamic attribute</a>
  <DIV ID="MixedCase"><?= 'x' ?></DIV>
  <p id="short"><?= $title ?></p><?= '<i id="shortecho">z</i>' ?>
  <?php include 'vendor/lib.php'; ?>
  <?php $o = getenv('VI_OUTSIDE_FILE'); if ($o && is_file($o)) { include $o; } ?>
</main>
<?php include 'partials/footer.php'; ?>
</body>
</html>

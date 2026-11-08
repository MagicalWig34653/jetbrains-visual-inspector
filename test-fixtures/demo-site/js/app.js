// Adds an element at runtime. It has no data-vi-src, so clicking it must report "unmappable".
document.addEventListener("DOMContentLoaded", () => {
  const note = document.getElementById("dynamic-note");
  if (note) note.innerHTML = '<p class="note">This paragraph exists only in the browser.</p>';
  const order = document.getElementById("order");
  if (order) order.addEventListener("click", () => alert("Ordered!"));
});

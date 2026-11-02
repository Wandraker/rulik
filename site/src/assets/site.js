// Копирование раскладок и номер последней версии с GitHub.
document.querySelectorAll("[data-copy]").forEach((btn) => {
  btn.addEventListener("click", async () => {
    const text = btn.closest(".layout").querySelector("textarea").value;
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      const ta = document.createElement("textarea");
      ta.value = text;
      document.body.append(ta);
      ta.select();
      document.execCommand("copy");
      ta.remove();
    }
    const old = btn.textContent;
    btn.textContent = btn.dataset.done;
    setTimeout(() => (btn.textContent = old), 1500);
  });
});

const ver = document.querySelectorAll("[data-version]");
if (ver.length) {
  fetch("https://api.github.com/repos/Wandraker/rulik/releases/latest")
    .then((r) => (r.ok ? r.json() : null))
    .then((rel) => {
      if (!rel || !rel.tag_name) return;
      const sizes = Object.fromEntries((rel.assets || []).map((a) => [a.name, (a.size / 1048576).toFixed(1)]));
      ver.forEach((el) => {
        const size = sizes[el.dataset.version];
        el.textContent = rel.tag_name + (size ? ` · ${size} MB` : "");
      });
    })
    .catch(() => {});
}

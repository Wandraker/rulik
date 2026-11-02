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

// Вики: поиск по статьям в боковом меню, Ctrl+K или / ставит в него курсор.
const search = document.querySelector("[data-wiki-search]");
if (search) {
  const links = [...document.querySelectorAll("[data-wiki-link]")];
  const groups = [...document.querySelectorAll("[data-wiki-group]")];
  const empty = document.querySelector("[data-wiki-empty]");
  const side = document.querySelector(".wiki-side details");
  let texts = {};
  let loading = null;
  // полный текст статей подгружается при первом поиске
  const load = () =>
    (loading ||= fetch("search.json")
      .then((r) => (r.ok ? r.json() : {}))
      .then((data) => (texts = data))
      .catch(() => {}));

  const filter = () => {
    const words = search.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
    let shown = 0;
    for (const link of links) {
      const hay = `${link.textContent} ${link.dataset.hint} ${texts[link.dataset.slug] || ""}`.toLowerCase();
      const match = words.every((w) => hay.includes(w));
      link.hidden = !match;
      if (match) shown++;
    }
    for (const g of groups) g.hidden = !g.querySelector("[data-wiki-link]:not([hidden])");
    empty.hidden = shown > 0;
  };

  search.addEventListener("focus", load);
  search.addEventListener("input", () => {
    filter();
    load().then(filter);
  });
  search.addEventListener("keydown", (e) => {
    if (e.key === "Enter") links.find((l) => !l.hidden)?.click();
    if (e.key === "Escape") search.blur();
  });
  document.addEventListener("keydown", (e) => {
    const typing = e.target.closest?.("input, textarea");
    if ((e.key === "k" && (e.ctrlKey || e.metaKey)) || (e.key === "/" && !typing)) {
      e.preventDefault();
      side.open = true;
      search.focus();
    }
  });
  // на телефоне меню разделов свёрнуто
  if (matchMedia("(max-width: 899px)").matches) side.open = false;
  // открытая статья видна в меню, даже если оно длиннее экрана
  const scroller = document.querySelector(".wiki-side");
  const current = scroller.querySelector("[aria-current]");
  if (current && scroller.scrollHeight > scroller.clientHeight) {
    scroller.scrollTop = current.offsetTop - scroller.offsetTop - scroller.clientHeight / 2;
  }
}

// «На этой странице»: заголовки статьи, текущий подсвечивается.
const toc = document.querySelector("[data-wiki-toc]");
if (toc) {
  const heads = [...document.querySelectorAll(".wiki-content h2")];
  if (heads.length > 1) {
    const items = heads.map((h, i) => {
      h.id ||= `part-${i + 1}`;
      const a = document.createElement("a");
      a.href = `#${h.id}`;
      a.textContent = h.textContent.trim();
      toc.append(a);
      return a;
    });
    const seen = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (!e.isIntersecting) continue;
          const i = heads.indexOf(e.target);
          items.forEach((a, j) => a.classList.toggle("is-active", i === j));
        }
      },
      { rootMargin: "-80px 0px -65% 0px" },
    );
    heads.forEach((h) => seen.observe(h));
  } else {
    toc.closest(".wiki-toc").classList.add("is-empty");
  }
}

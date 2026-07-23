// Fullscreen lightbox: full-res image, arrow-key navigation, download.

import { Photo, imageUrl, download } from "./api";

export interface Viewer {
  open(photos: Photo[], index: number): void;
}

export function createViewer(): Viewer {
  const overlay = document.createElement("div");
  overlay.className = "viewer hidden";
  overlay.innerHTML = `
    <button class="v-close" title="Close (Esc)">✕</button>
    <button class="v-nav v-prev" title="Previous (←)">‹</button>
    <button class="v-nav v-next" title="Next (→)">›</button>
    <button class="v-download" title="Download original">Download</button>
    <img class="v-img" alt="" />
    <video class="v-video" controls></video>
    <div class="v-spinner"></div>
  `;
  document.body.appendChild(overlay);

  const img = overlay.querySelector<HTMLImageElement>(".v-img")!;
  const video = overlay.querySelector<HTMLVideoElement>(".v-video")!;
  const spinner = overlay.querySelector<HTMLElement>(".v-spinner")!;
  const btnClose = overlay.querySelector<HTMLElement>(".v-close")!;
  const btnPrev = overlay.querySelector<HTMLElement>(".v-prev")!;
  const btnNext = overlay.querySelector<HTMLElement>(".v-next")!;
  const btnDl = overlay.querySelector<HTMLButtonElement>(".v-download")!;

  let list: Photo[] = [];
  let i = 0;

  function show(): void {
    const p = list[i];
    if (!p) return;
    spinner.classList.remove("hidden");
    img.classList.add("hidden");
    video.classList.add("hidden");
    if (p.v) {
      video.src = imageUrl(p);
      video.addEventListener("loadedmetadata", onMediaReady, { once: true });
      video.addEventListener("error", onMediaError, { once: true });
    } else {
      img.src = imageUrl(p);
      img.addEventListener("load", onMediaReady, { once: true });
      img.addEventListener("error", onMediaError, { once: true });
    }
    btnPrev.style.visibility = i > 0 ? "visible" : "hidden";
    btnNext.style.visibility = i < list.length - 1 ? "visible" : "hidden";
  }

  function onMediaReady(): void {
    spinner.classList.add("hidden");
    const p = list[i];
    if (p.v) video.classList.remove("hidden");
    else img.classList.remove("hidden");
  }

  function onMediaError(): void {
    spinner.classList.add("hidden");
  }

  function go(delta: number): void {
    const n = i + delta;
    if (n >= 0 && n < list.length) { i = n; show(); }
  }

  function close(): void {
    overlay.classList.add("hidden");
    img.src = "";
    video.src = "";
  }

  btnClose.addEventListener("click", close);
  btnPrev.addEventListener("click", () => go(-1));
  btnNext.addEventListener("click", () => go(1));
  btnDl.addEventListener("click", async () => {
    btnDl.disabled = true;
    btnDl.textContent = "Saving…";
    try { await download(list[i]); } catch { /* ignore */ }
    btnDl.disabled = false;
    btnDl.textContent = "Download";
  });
  overlay.addEventListener("click", (e) => { if (e.target === overlay) close(); });

  window.addEventListener("keydown", (e) => {
    if (overlay.classList.contains("hidden")) return;
    if (e.key === "Escape") close();
    else if (e.key === "ArrowLeft") go(-1);
    else if (e.key === "ArrowRight") go(1);
  });

  return {
    open(photos, index) {
      list = photos; i = index;
      overlay.classList.remove("hidden");
      show();
    },
  };
}

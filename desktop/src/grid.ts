// Photos-style grid: square tiles, month section headers, infinite scroll.
// Virtualization via CSS `content-visibility:auto` — the browser skips painting
// off-screen month sections, so 50k+ photos scroll smoothly without manual windowing.

import { Photo, getPage, thumbUrl } from "./api";

const PAGE = 300; // photos per fetch

export interface GridView {
  el: HTMLElement;
  destroy(): void;
}

function monthLabel(ts: number): string {
  const d = new Date(ts);
  if (isNaN(d.getTime())) return "Unknown Date";
  return d.toLocaleDateString("en-US", { year: "numeric", month: "long" });
}

export function createGrid(onSelect: (photos: Photo[], index: number) => void): GridView {
  const root = document.createElement("div");
  root.className = "grid-root";

  const scroller = document.createElement("div");
  scroller.className = "grid-scroll";
  root.appendChild(scroller);

  const sentinel = document.createElement("div");
  sentinel.className = "grid-sentinel";
  scroller.appendChild(sentinel);

  const status = document.createElement("div");
  status.className = "grid-status";
  scroller.appendChild(status);

  let total = 0;
  let loaded = 0;
  let loading = false;
  let done = false;
  const all: Photo[] = []; // flat, for the viewer
  const monthSections = new Map<string, HTMLElement>();

  function getMonthTilesContainer(label: string): HTMLElement {
    const existing = monthSections.get(label);
    if (existing) return existing;

    const section = document.createElement("section");
    section.className = "month";

    const h2 = document.createElement("h2");
    h2.textContent = label;
    section.appendChild(h2);

    const tiles = document.createElement("div");
    tiles.className = "tiles";
    section.appendChild(tiles);

    scroller.insertBefore(section, sentinel);
    monthSections.set(label, tiles);
    return tiles;
  }

  // Append photos to the DOM, placing each tile in its corresponding month section.
  function append(photos: Photo[]): void {
    for (const p of photos) {
      const label = monthLabel(p.date);
      const tilesContainer = getMonthTilesContainer(label);

      const idx = all.length;
      all.push(p);

      const tile = document.createElement("button");
      tile.className = "tile";
      tile.type = "button";

      // Videos show a play icon overlay on the thumbnail.
      if (p.v) {
        const playIcon = document.createElement("div");
        playIcon.className = "play-icon";
        playIcon.textContent = "▶";
        tile.appendChild(playIcon);
      }

      const img = document.createElement("img");
      img.loading = "lazy";
      img.decoding = "async";
      img.src = thumbUrl(p);
      img.alt = "";
      tile.appendChild(img);

      tile.addEventListener("click", () => onSelect(all, idx));
      tilesContainer.appendChild(tile);
    }
  }

  async function loadMore(): Promise<void> {
    if (loading || done) return;
    loading = true;
    status.textContent = "Loading…";
    try {
      const page = Math.floor(loaded / PAGE);
      const res = await getPage(page);
      total = res.total;
      append(res.items);
      loaded += res.items.length;
      if (res.items.length === 0 || loaded >= total) {
        done = true;
        status.textContent = total === 0 ? "No photos" : `${total.toLocaleString()} photos`;
      } else {
        status.textContent = "";
      }
    } catch (e) {
      status.textContent = (e as Error).message;
      done = true; // stop hammering a broken connection
    } finally {
      loading = false;
    }
  }

  // Fire loadMore whenever the sentinel nears the viewport.
  const io = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) loadMore();
    },
    { root: scroller, rootMargin: "600px" }
  );
  io.observe(sentinel);

  loadMore(); // initial

  return {
    el: root,
    destroy() {
      io.disconnect();
      root.remove();
    },
  };
}

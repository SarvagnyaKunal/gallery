// Photos-style grid: square tiles, month section headers, infinite scroll.
// Virtualization via CSS `content-visibility:auto` — the browser skips painting
// off-screen month sections, so 50k+ photos scroll smoothly without manual windowing.
import { getPage, thumbUrl } from "./api";
const PAGE = 300; // photos per fetch
function monthLabel(ts) {
    return new Date(ts).toLocaleDateString("en-US", { year: "numeric", month: "long" });
}
export function createGrid(onSelect) {
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
    const all = []; // flat, for the viewer
    let curMonth = "";
    let curTiles = null; // active month's tile container
    // Append photos to the DOM, opening a new month section when the month changes.
    function append(photos) {
        for (const p of photos) {
            const m = monthLabel(p.date);
            if (m !== curMonth) {
                curMonth = m;
                const section = document.createElement("section");
                section.className = "month";
                const h = document.createElement("h2");
                h.textContent = m;
                section.appendChild(h);
                curTiles = document.createElement("div");
                curTiles.className = "tiles";
                section.appendChild(curTiles);
                scroller.insertBefore(section, sentinel);
            }
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
            curTiles.appendChild(tile);
        }
    }
    async function loadMore() {
        if (loading || done)
            return;
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
            }
            else {
                status.textContent = "";
            }
        }
        catch (e) {
            status.textContent = e.message;
            done = true; // stop hammering a broken connection
        }
        finally {
            loading = false;
        }
    }
    // Fire loadMore whenever the sentinel nears the viewport.
    const io = new IntersectionObserver((entries) => {
        if (entries.some((e) => e.isIntersecting))
            loadMore();
    }, { root: scroller, rootMargin: "600px" });
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

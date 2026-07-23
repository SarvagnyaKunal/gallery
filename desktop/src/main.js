// App shell: connect screen (IP + OTP) → gallery. Remembers device after first pair.
import "./styles.css";
import { isPaired, pair, forget, wake, sleep, rememberedAddress } from "./api";
import { createGrid } from "./grid";
import { createViewer } from "./viewer";
const app = document.getElementById("app");
const viewer = createViewer();
let grid = null;
let heartbeat = null;
const delay = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms));
function stopGallerySession() {
    if (heartbeat != null) {
        window.clearInterval(heartbeat);
        heartbeat = null;
    }
}
async function startGallerySession() {
    stopGallerySession();
    let lastError;
    for (let attempt = 0; attempt < 5; attempt += 1) {
        try {
            await wake();
            lastError = null;
            break;
        }
        catch (ex) {
            lastError = ex;
            if (ex.message === "Device no longer paired")
                break;
            await delay(1500);
        }
    }
    if (lastError)
        throw lastError;
    heartbeat = window.setInterval(() => {
        wake().catch(() => { });
    }, 15000);
}
function renderConnect(err = "") {
    stopGallerySession();
    grid?.destroy();
    grid = null;
    app.innerHTML = `
    <div class="connect">
      <h1>Gallery</h1>
      <p class="sub">Enter your phone's address and the code shown in the app.</p>
      <form id="pair-form">
        <input id="ip" placeholder="192.168.1.5:65501" autocomplete="off" spellcheck="false" />
        <input id="otp" placeholder="6-digit code" inputmode="numeric" maxlength="6" autocomplete="off" />
        <button type="submit">Connect</button>
        <div class="err">${err}</div>
      </form>
    </div>`;
    const form = app.querySelector("#pair-form");
    const ip = app.querySelector("#ip");
    const otp = app.querySelector("#otp");
    const errEl = app.querySelector(".err");
    const btn = form.querySelector("button");
    ip.value = rememberedAddress();
    ip.focus();
    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        btn.disabled = true;
        errEl.textContent = "";
        try {
            await pair(ip.value, otp.value);
            await renderGallery();
        }
        catch (ex) {
            errEl.textContent = ex.message;
            btn.disabled = false;
        }
    });
}
function renderFindingPhone() {
    stopGallerySession();
    grid?.destroy();
    grid = null;
    app.innerHTML = `
    <div class="connect">
      <h1>Gallery</h1>
      <p class="sub">Looking for your remembered phone at ${rememberedAddress()}.</p>
      <div class="err"></div>
    </div>`;
}
async function renderGallery() {
    try {
        await startGallerySession();
    }
    catch (ex) {
        renderConnect(ex.message);
        return;
    }
    app.innerHTML = "";
    const bar = document.createElement("header");
    bar.className = "topbar";
    bar.innerHTML = `<span class="title">Gallery</span><button id="disconnect">Disconnect</button>`;
    app.appendChild(bar);
    bar.querySelector("#disconnect").addEventListener("click", () => {
        sleep();
        stopGallerySession();
        forget();
        renderConnect();
    });
    grid = createGrid((photos, index) => viewer.open(photos, index));
    app.appendChild(grid.el);
}
window.addEventListener("pagehide", () => {
    sleep();
    stopGallerySession();
});
if (isPaired()) {
    renderFindingPhone();
    void renderGallery();
}
else
    renderConnect();

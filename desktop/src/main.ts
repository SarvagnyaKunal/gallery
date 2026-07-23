// App shell: connect screen (IP + OTP) → gallery. Remembers device after first pair.

import "./styles.css";
import { isPaired, pair, sleep, unpair, rememberedAddress, runLocalWakeSender, waitForPhoneServer } from "./api";
import { createGrid, GridView } from "./grid";
import { createViewer } from "./viewer";

const app = document.getElementById("app")!;
const viewer = createViewer();
let grid: GridView | null = null;

const delay = (ms: number): Promise<void> => new Promise((resolve) => window.setTimeout(resolve, ms));

async function startGallerySession(): Promise<void> {
  await runLocalWakeSender();
  let lastError: unknown;
  for (let attempt = 0; attempt < 4; attempt += 1) {
    try {
      await waitForPhoneServer();
      lastError = null;
      break;
    } catch (ex) {
      lastError = ex;
      if ((ex as Error).message === "Device no longer paired") break;
      await delay(1000);
    }
  }
  if (lastError) throw lastError;
}

function renderConnect(err = ""): void {
  grid?.destroy(); grid = null;
  const paired = isPaired();

  app.innerHTML = `
    <div class="connect">
      <h1>Gallery</h1>
      <p class="sub">${paired ? `Paired with <strong>${rememberedAddress()}</strong>` : "Enter your phone's address and the code shown in the app."}</p>
      <form id="pair-form">
        <input id="ip" placeholder="192.168.1.5:65501" autocomplete="off" spellcheck="false" />
        <input id="otp" placeholder="${paired ? '6-digit code (optional if paired)' : '6-digit code'}" inputmode="numeric" maxlength="6" autocomplete="off" />
        <div class="connect-btns">
          <button type="submit" id="connect-btn">${paired ? "Reconnect" : "Connect"}</button>
          ${paired ? `<button type="button" id="unpair-btn" class="btn-unpair">Unpair</button>` : ""}
        </div>
        <div class="err">${err}</div>
      </form>
    </div>`;

  const form = app.querySelector<HTMLFormElement>("#pair-form")!;
  const ip = app.querySelector<HTMLInputElement>("#ip")!;
  const otp = app.querySelector<HTMLInputElement>("#otp")!;
  const errEl = app.querySelector<HTMLElement>(".err")!;
  const btn = form.querySelector<HTMLButtonElement>("#connect-btn")!;
  const unpairBtn = form.querySelector<HTMLButtonElement>("#unpair-btn");
  ip.value = rememberedAddress();
  if (!paired) ip.focus();

  if (unpairBtn) {
    unpairBtn.addEventListener("click", () => {
      unpair();
      renderConnect("Device unpaired.");
    });
  }

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    btn.disabled = true; errEl.textContent = "";
    try {
      if (otp.value.trim()) {
        await pair(ip.value, otp.value);
      } else if (!isPaired()) {
        throw new Error("Enter the pairing code from your phone");
      }
      await renderGallery();
    } catch (ex) {
      errEl.textContent = (ex as Error).message;
      btn.disabled = false;
    }
  });
}

function renderFindingPhone(): void {
  grid?.destroy(); grid = null;
  app.innerHTML = `
    <div class="connect">
      <h1>Gallery</h1>
      <p class="sub">Looking for your remembered phone at ${rememberedAddress()}.</p>
      <div class="err"></div>
    </div>`;
}

async function renderGallery(): Promise<void> {
  try {
    await startGallerySession();
  } catch (ex) {
    renderConnect((ex as Error).message);
    return;
  }

  app.innerHTML = "";
  const bar = document.createElement("header");
  bar.className = "topbar";
  bar.innerHTML = `
    <span class="title">Gallery</span>
    <div class="actions">
      <button id="disconnect" class="btn-disconnect">Disconnect</button>
      <button id="unpair" class="btn-unpair">Unpair</button>
    </div>`;
  app.appendChild(bar);

  bar.querySelector("#disconnect")!.addEventListener("click", () => {
    sleep();
    renderConnect("Disconnected. App stopped on phone.");
  });

  bar.querySelector("#unpair")!.addEventListener("click", () => {
    unpair();
    renderConnect("Unpaired device.");
  });

  grid = createGrid((photos, index) => viewer.open(photos, index));
  app.appendChild(grid.el);
}

if (isPaired()) {
  renderFindingPhone();
  void renderGallery();
}
else renderConnect();

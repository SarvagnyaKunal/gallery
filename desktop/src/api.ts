// Talks to the phone's LAN server. Connection (base URL + token) persists in localStorage.

export interface Photo { id: number; date: number; w: number; h: number; v?: boolean }
export interface Page { total: number; items: Photo[]; fcmToken?: string }

const LS_BASE = "gv.base";
const LS_TOKEN = "gv.token";
const LS_FCM_TOKEN = "gv.fcmToken";
const DEFAULT_PHONE_PORT = "65501";

let base = localStorage.getItem(LS_BASE) || "";
let token = localStorage.getItem(LS_TOKEN) || "";

try {
  const saved = new URL(base);
  if (saved.port !== DEFAULT_PHONE_PORT) {
    saved.port = DEFAULT_PHONE_PORT;
    base = saved.toString().replace(/\/$/, "");
    localStorage.setItem(LS_BASE, base);
  }
} catch {
  // Ignore old malformed saved values; pairing will repair them.
}

export function isPaired(): boolean {
  return !!base && !!token;
}

export function rememberedAddress(): string {
  return base.replace(/^https?:\/\//i, "");
}

export function forget(): void {
  base = ""; token = "";
  localStorage.removeItem(LS_BASE);
  localStorage.removeItem(LS_TOKEN);
  localStorage.removeItem(LS_FCM_TOKEN);
}

async function persistFcmToken(fcmToken: unknown): Promise<void> {
  if (typeof fcmToken !== "string" || !fcmToken) return;
  if (localStorage.getItem(LS_FCM_TOKEN) === fcmToken) return;
  localStorage.setItem(LS_FCM_TOKEN, fcmToken);
  try {
    await fetch("/__desktop/fcm-token", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: fcmToken }),
    });
  } catch {
    // The static build may not have the local helper; localStorage still keeps it.
  }
}

export async function runLocalWakeSender(): Promise<void> {
  try {
    await fetch("/__desktop/wake", { method: "POST" });
  } catch {
    // If the local helper is unavailable, continue with the normal LAN flow.
  }
}

// Parse a user-typed address into a valid "http://host:port".
// Accepts: "192.168.1.5", "192.168.1.5:65501", "http://192.168.1.5:65501",
// and the common typo "192.168.1.5.65501" (dot instead of colon before port).
// Throws a clear message if the result isn't a usable URL.
function normalize(ip: string): string {
  let s = ip.trim().replace(/^https?:\/\//i, ""); // drop scheme if present
  s = s.replace(/\/.*$/, "");                      // drop any path
  if (!s) throw new Error("Enter your phone's address");

  let host = s;
  let port = DEFAULT_PHONE_PORT;

  if (s.includes(":")) {
    // Explicit host:port.
    const i = s.lastIndexOf(":");
    host = s.slice(0, i);
    port = s.slice(i + 1);
  } else {
    // Typo case: an IPv4 has 4 octets; a 5th dotted group is really the port.
    const typo = s.match(/^(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\.(\d{2,5})$/);
    if (typo) { host = typo[1]; port = typo[2]; }
  }

  if (!host || !/^\d{1,5}$/.test(port)) {
    throw new Error("Address should look like 192.168.1.5:65501");
  }

  const url = `http://${host}:${port}`;
  try {
    new URL(url); // final sanity check
  } catch {
    throw new Error("Invalid address");
  }
  return url;
}

// Pair with the phone using its one-time code. Stores base+token on success.
export async function pair(ip: string, otp: string): Promise<void> {
  const b = normalize(ip); // may throw with a clear message on bad input
  await runLocalWakeSender();
  let res: Response;
  try {
    res = await fetch(`${b}/pair?otp=${encodeURIComponent(otp.trim())}`);
  } catch {
    throw new Error(`Can't reach ${b}. Same Wi-Fi? Is sharing on?`);
  }
  if (!res.ok) throw new Error(res.status === 403 ? "Wrong code" : `Server error (${res.status})`);
  const data = await res.json();
  if (!data.token) throw new Error("No token returned");
  base = b; token = data.token;
  localStorage.setItem(LS_BASE, base);
  localStorage.setItem(LS_TOKEN, token);
  await persistFcmToken(data.fcmToken);
}

export async function getPage(page: number): Promise<Page> {
  const res = await fetch(`${base}/gallery?page=${page}&t=${token}`);
  if (res.status === 403) { forget(); throw new Error("Device no longer paired");
  }
  if (!res.ok) throw new Error(`Failed to load page ${page}`);
  const data = await res.json();
  await persistFcmToken(data.fcmToken);
  return data;
}

export async function waitForPhoneServer(): Promise<void> {
  if (!isPaired()) return;
  let res: Response;
  try {
    res = await fetch(`${base}/gallery?page=0&t=${token}`);
  } catch {
    throw new Error(`Can't reach ${rememberedAddress()}. Is sharing turned on in the phone app?`);
  }
  if (res.status === 403) { forget(); throw new Error("Device no longer paired"); }
  if (!res.ok) throw new Error("Phone sharing is not available");
  await persistFcmToken((await res.json()).fcmToken);
}

export function sleep(): void {
  if (!isPaired()) return;
  fetch(`${base}/sleep?t=${token}`, { keepalive: true }).catch(() => {});
}

export function unpair(): void {
  if (isPaired()) {
    fetch(`${base}/unpair?t=${token}`, { keepalive: true }).catch(() => {});
  }
  forget();
}

export const thumbUrl = (photo: Photo): string => {
  const suffix = photo.v ? "v" : "";
  return `${base}/thumb/${photo.id}${suffix}?t=${token}`;
};

export const imageUrl = (photo: Photo): string => {
  const suffix = photo.v ? "v" : "";
  return `${base}/image/${photo.id}${suffix}?t=${token}`;
};

// Download original via blob so the browser saves rather than navigates.
export async function download(photo: Photo): Promise<void> {
  const res = await fetch(imageUrl(photo));
  if (!res.ok) throw new Error("Download failed");
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `${photo.v ? "VID" : "IMG"}_${photo.id}.${photo.v ? "mp4" : "jpg"}`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

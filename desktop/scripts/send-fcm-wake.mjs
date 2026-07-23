import { createHash, createSign } from "node:crypto";
import { readFile, readdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

async function readEnv() {
  const env = { ...process.env };
  try {
    const text = await readFile(resolve(root, ".env"), "utf8");
    for (const line of text.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) continue;
      const match = trimmed.match(/^([^=]+)=(.*)$/);
      if (!match) continue;
      const key = match[1].trim();
      const value = match[2].trim().replace(/^["']|["']$/g, "");
      if (!(key in env)) env[key] = value;
    }
  } catch {}

  try {
    env.FCM_REGISTRATION_TOKEN ||= (await readFile(resolve(root, "fcm-token.txt"), "utf8")).trim();
  } catch {}

  return env;
}

function base64url(input) {
  return Buffer.from(input).toString("base64url");
}

async function loadServiceAccount(env) {
  if (env.FIREBASE_SERVICE_ACCOUNT_JSON) return JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
  if (env.GOOGLE_APPLICATION_CREDENTIALS) {
    return JSON.parse(await readFile(resolve(root, env.GOOGLE_APPLICATION_CREDENTIALS), "utf8"));
  }
  for (const entry of await readdir(root, { withFileTypes: true })) {
    if (!entry.isFile() || !entry.name.endsWith(".json")) continue;
    try {
      const candidate = JSON.parse(await readFile(resolve(root, entry.name), "utf8"));
      if (candidate.type === "service_account" && candidate.client_email && candidate.private_key) {
        return candidate;
      }
    } catch {}
  }
  return null;
}

async function getAccessToken(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now,
  };
  const unsigned = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claim))}`;
  const signature = createSign("RSA-SHA256").update(unsigned).sign(serviceAccount.private_key, "base64url");
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${unsigned}.${signature}`,
    }),
  });
  if (!res.ok) throw new Error(`OAuth failed (${res.status})`);
  return (await res.json()).access_token;
}

async function sendWake() {
  const env = await readEnv();
  const token = env.FCM_REGISTRATION_TOKEN;
  if (!token) {
    console.log("No FCM_REGISTRATION_TOKEN stored; skipping wake send.");
    return;
  }

  const projectId = env.FCM_PROJECT_ID || JSON.parse(await readFile(resolve(root, "../android/app/google-services.json"), "utf8")).project_info.project_id;
  // Data-only high-priority message: Android calls onMessageReceived immediately in background.
  const message = {
    message: {
      token,
      data: {
        action: env.FCM_WAKE_ACTION || "wake",
        nonce: createHash("sha256").update(`${Date.now()}:${token}`).digest("hex").slice(0, 16),
      },
      android: {
        priority: "HIGH",
      },
    },
  };

  const serviceAccount = await loadServiceAccount(env);
  if (!serviceAccount) {
    console.log("No Firebase service account configured; skipping wake send.");
    return;
  }

  const accessToken = await getAccessToken(serviceAccount);
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(message),
  });
  if (!res.ok) throw new Error(`FCM send failed (${res.status}): ${await res.text()}`);
}

sendWake().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});

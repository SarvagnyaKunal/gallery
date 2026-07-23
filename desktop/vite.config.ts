import { defineConfig } from "vite";
import { spawn } from "node:child_process";
import { readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const desktopDir = dirname(fileURLToPath(import.meta.url));
const envPath = resolve(desktopDir, ".env");
const senderPath = resolve(desktopDir, "scripts", "send-fcm-wake.mjs");

function localDesktopBridge() {
  return {
    name: "local-desktop-bridge",
    configureServer(server) {
      server.middlewares.use("/__desktop/fcm-token", async (req, res) => {
        if (req.method !== "POST") {
          res.statusCode = 405;
          res.end();
          return;
        }

        const body = await new Promise((resolveBody) => {
          let data = "";
          req.on("data", (chunk) => { data += chunk; });
          req.on("end", () => resolveBody(data));
        });
        const token = JSON.parse(String(body || "{}")).token;
        if (typeof token !== "string" || !token) {
          res.statusCode = 400;
          res.end(JSON.stringify({ ok: false }));
          return;
        }

        let env = "";
        try { env = await readFile(envPath, "utf8"); } catch {}
        const line = `FCM_REGISTRATION_TOKEN=${token}`;
        env = env.match(/^FCM_REGISTRATION_TOKEN=/m)
          ? env.replace(/^FCM_REGISTRATION_TOKEN=.*$/m, line)
          : `${env.replace(/\s*$/, "")}${env.trim() ? "\n" : ""}${line}\n`;
        await writeFile(envPath, env, "utf8");
        await writeFile(resolve(desktopDir, "fcm-token.txt"), `${token}\n`, "utf8");
        res.setHeader("Content-Type", "application/json");
        res.end(JSON.stringify({ ok: true }));
      });

      server.middlewares.use("/__desktop/wake", async (req, res) => {
        if (req.method !== "POST") {
          res.statusCode = 405;
          res.end();
          return;
        }

        const child = spawn(process.execPath, [senderPath], { cwd: desktopDir, windowsHide: true });
        let output = "";
        child.stdout.on("data", (chunk) => { output += chunk; });
        child.stderr.on("data", (chunk) => { output += chunk; });
        child.on("close", (code) => {
          res.setHeader("Content-Type", "application/json");
          res.end(JSON.stringify({ ok: code === 0, code, output }));
        });
      });
    },
  };
}

export default defineConfig({
  plugins: [localDesktopBridge()],
  server: {
    host: "192.168.0.175",
    port: 65502,
  },
});

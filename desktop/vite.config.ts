import { defineConfig } from "vite";

export default defineConfig({
  server: {
    host: true, // listen on 0.0.0.0 so the dev server is reachable at the LAN IP, not just localhost
    port: 65502,
  },
});

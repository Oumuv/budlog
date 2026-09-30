import { defineConfig } from "vite";
import uni from "@dcloudio/vite-plugin-uni";

const PUBLIC_VERSION_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$/;
const builtAt = new Date().toISOString();

function readBuildId(): string | undefined {
  const name = "BUDLOG_BUILD_ID";
  const value = process.env[name]?.trim();
  if (!value) return undefined;
  if (!PUBLIC_VERSION_PATTERN.test(value)) {
    throw new Error(`${name} must contain 1-64 letters, numbers, dots, underscores, plus signs, or hyphens`);
  }
  return value;
}

const buildId = readBuildId() || `h5-${builtAt.replace(/\D/g, "")}`;
const releaseManifest = {
  buildId,
  builtAt,
};

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    uni(),
    {
      name: "budlog-release-manifest",
      apply: "build",
      generateBundle() {
        this.emitFile({
          type: "asset",
          fileName: "version.json",
          source: `${JSON.stringify(releaseManifest, null, 2)}\n`,
        });
      },
    },
  ],
  define: {
    __BUDLOG_BUILD_ID__: JSON.stringify(buildId),
    __BUDLOG_BUILT_AT__: JSON.stringify(builtAt),
  },
  publicDir: "public",
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
    },
  },
});

import { readFileSync } from "node:fs";
import { defineConfig, loadEnv } from "vite";
import uni from "@dcloudio/vite-plugin-uni";

interface PackageMetadata {
  version: string;
}

interface ManifestMetadata {
  versionName: string;
}

const packageMetadata = JSON.parse(
  readFileSync(new URL("./package.json", import.meta.url), "utf8"),
) as PackageMetadata;
const manifestMetadata = JSON.parse(
  readFileSync(new URL("./src/manifest.json", import.meta.url), "utf8"),
) as ManifestMetadata;

if (manifestMetadata.versionName !== packageMetadata.version) {
  throw new Error("package.json version must match manifest.json versionName.");
}

function validateProductionUrl(rawUrl: string | undefined): void {
  if (!rawUrl?.trim()) {
    throw new Error("Production build requires VITE_BUDLOG_H5_URL.");
  }

  let url: URL;
  try {
    url = new URL(rawUrl.trim());
  } catch {
    throw new Error("VITE_BUDLOG_H5_URL must be an absolute URL.");
  }

  if (url.protocol !== "https:") {
    throw new Error("Production VITE_BUDLOG_H5_URL must use HTTPS.");
  }
  if (url.username || url.password) {
    throw new Error("VITE_BUDLOG_H5_URL must not contain credentials.");
  }
  if (url.search || url.hash) {
    throw new Error("VITE_BUDLOG_H5_URL must not contain query parameters or a fragment.");
  }
}

export default defineConfig(({ command, mode }) => {
  const fileEnv = loadEnv(mode, process.cwd(), "VITE_");
  const configuredUrl = process.env.VITE_BUDLOG_H5_URL ?? fileEnv.VITE_BUDLOG_H5_URL;

  if (command === "build" && mode === "production") {
    validateProductionUrl(configuredUrl);
  }

  return {
    plugins: [uni()],
    define: {
      __BUDLOG_SHELL_VERSION__: JSON.stringify(packageMetadata.version),
    },
  };
});

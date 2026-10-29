import { runtime } from "./runtime";

export interface ReleaseManifest {
  buildId: string;
  builtAt: string;
}

const VERSION_CHECK_INTERVAL_MS = 5 * 60 * 1000;
const VERSION_PROMPT_INTERVAL_MS = 30 * 60 * 1000;
const PUBLIC_VERSION_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$/;

export const currentRelease: Readonly<ReleaseManifest> = Object.freeze({
  buildId: __BUDLOG_BUILD_ID__,
  builtAt: __BUDLOG_BUILT_AT__,
});

let lastCheckAt = 0;
let lastPromptAt = 0;
let lastPromptedBuildId = "";
let checkInFlight: Promise<void> | undefined;

function parseManifest(value: unknown): ReleaseManifest {
  if (!value || typeof value !== "object") throw new Error("version.json is not an object");
  const manifest = value as Record<string, unknown>;
  const buildId = typeof manifest.buildId === "string" ? manifest.buildId.trim() : "";
  const builtAt = typeof manifest.builtAt === "string" ? manifest.builtAt.trim() : "";

  if (!PUBLIC_VERSION_PATTERN.test(buildId)) throw new Error("version.json contains an invalid buildId");
  if (!builtAt || Number.isNaN(Date.parse(builtAt))) throw new Error("version.json contains an invalid builtAt");

  return { buildId, builtAt };
}

async function fetchLatestRelease(): Promise<ReleaseManifest> {
  const url = new URL("/version.json", window.location.origin);
  url.searchParams.set("_", String(Date.now()));
  const response = await fetch(url.toString(), {
    cache: "no-store",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error(`version.json request failed with HTTP ${response.status}`);
  return parseManifest(await response.json());
}

function shouldPrompt(buildId: string, now: number): boolean {
  return buildId !== lastPromptedBuildId || now - lastPromptAt >= VERSION_PROMPT_INTERVAL_MS;
}

function promptForRefresh(release: ReleaseManifest): Promise<void> {
  const now = Date.now();
  if (!shouldPrompt(release.buildId, now)) return Promise.resolve();
  lastPromptedBuildId = release.buildId;
  lastPromptAt = now;

  return new Promise((resolve) => {
    uni.showModal({
      title: "发现新版本",
      content: "新版页面已发布。刷新会重新加载当前页面，请先保存正在编辑的内容。",
      confirmText: "刷新",
      cancelText: "稍后",
      success: (result) => {
        if (result.confirm) window.location.reload();
        resolve();
      },
      fail: (error) => {
        console.warn("Budlog release prompt failed", error);
        resolve();
      },
    });
  });
}

async function performVersionCheck(): Promise<void> {
  try {
    const latestRelease = await fetchLatestRelease();
    if (latestRelease.buildId !== currentRelease.buildId) await promptForRefresh(latestRelease);
  } catch (error) {
    console.warn("Budlog version check failed", {
      client: runtime.client,
      message: error instanceof Error ? error.message : "unknown error",
    });
  }
}

export function checkForH5Update(): Promise<void> {
  if (!import.meta.env.PROD || typeof window === "undefined") return Promise.resolve();
  if (checkInFlight) return checkInFlight;

  const now = Date.now();
  if (now - lastCheckAt < VERSION_CHECK_INTERVAL_MS) return Promise.resolve();
  lastCheckAt = now;

  const check = performVersionCheck();
  checkInFlight = check;
  void check.finally(() => {
    if (checkInFlight === check) checkInFlight = undefined;
  });
  return check;
}

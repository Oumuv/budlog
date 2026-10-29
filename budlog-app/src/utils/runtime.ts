export type RuntimeClient = "browser" | "android-app";

export interface RuntimeContext {
  client: RuntimeClient;
  isAndroidApp: boolean;
  shellVersion?: string;
  bridgeVersion?: string;
}

const ANDROID_APP_CLIENT = "android-app";
const PUBLIC_VERSION_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$/;

function readPublicVersion(params: URLSearchParams, name: string): string | undefined {
  const value = params.get(name)?.trim();
  return value && PUBLIC_VERSION_PATTERN.test(value) ? value : undefined;
}

function detectRuntime(): RuntimeContext {
  if (typeof window === "undefined") {
    return Object.freeze({ client: "browser", isAndroidApp: false });
  }

  const params = new URLSearchParams(window.location.search);
  const isAndroidApp = params.get("client") === ANDROID_APP_CLIENT;
  if (!isAndroidApp) {
    return Object.freeze({ client: "browser", isAndroidApp: false });
  }

  return Object.freeze({
    client: ANDROID_APP_CLIENT,
    isAndroidApp: true,
    shellVersion: readPublicVersion(params, "shellVersion"),
    bridgeVersion: readPublicVersion(params, "bridgeVersion"),
  });
}

// Startup parameters identify presentation context only and must never grant native capabilities.
export const runtime: Readonly<RuntimeContext> = detectRuntime();

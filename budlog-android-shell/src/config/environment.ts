export const BRIDGE_VERSION = "1";

export interface ShellEnvironment {
  allowedOrigin: string;
  allowedOriginPattern: string;
  shellVersion: string;
  startUrl: string;
}

export interface ParsedHttpsUrl {
  hash: string;
  host: string;
  href: string;
  origin: string;
  search: string;
}

export type ShellEnvironmentResult =
  | { ok: true; value: ShellEnvironment }
  | { ok: false; message: string };

function escapeRegularExpression(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function normalizeAuthority(authority: string): string | null {
  if (!authority || authority.includes("@")) return null;

  let hostname = authority;
  let port = "";
  if (authority.startsWith("[")) {
    const closingBracket = authority.indexOf("]");
    if (closingBracket <= 1) return null;
    hostname = authority.slice(0, closingBracket + 1);
    const remainder = authority.slice(closingBracket + 1);
    if (remainder) {
      if (!remainder.startsWith(":")) return null;
      port = remainder.slice(1);
    }
    if (!/^\[[0-9a-f:.]+\]$/i.test(hostname)) return null;
  } else {
    const firstColon = authority.indexOf(":");
    const lastColon = authority.lastIndexOf(":");
    if (firstColon !== lastColon) return null;
    if (lastColon >= 0) {
      hostname = authority.slice(0, lastColon);
      port = authority.slice(lastColon + 1);
    }
    if (!/^[a-z0-9.-]+$/i.test(hostname)) return null;
  }

  if (!hostname || hostname.startsWith(".") || hostname.endsWith(".") || hostname.includes("..")) {
    return null;
  }
  if (!port) return hostname.toLowerCase();
  if (!/^\d{1,5}$/.test(port)) return null;

  const portNumber = Number(port);
  if (portNumber < 1 || portNumber > 65_535) return null;
  return portNumber === 443
    ? hostname.toLowerCase()
    : `${hostname.toLowerCase()}:${portNumber}`;
}

export function parseHttpsUrl(value: string): ParsedHttpsUrl | null {
  if (!value || value !== value.trim() || /[\u0000-\u0020\u007f\\]/.test(value)) {
    return null;
  }

  const match = /^https:\/\/([^/?#]+)(\/[^?#]*)?(\?[^#]*)?(#.*)?$/i.exec(value);
  if (!match) return null;

  const host = normalizeAuthority(match[1]);
  if (!host) return null;

  const path = match[2] ?? "";
  const search = match[3] ?? "";
  const hash = match[4] ?? "";
  const origin = `https://${host}`;
  return {
    hash,
    host,
    href: `${origin}${path}${search}${hash}`,
    origin,
    search,
  };
}

export function resolveShellEnvironment(): ShellEnvironmentResult {
  const configuredUrl = import.meta.env.VITE_BUDLOG_H5_URL?.trim();
  if (!configuredUrl) {
    return { ok: false, message: "缺少 H5 地址配置，当前版本无法启动。" };
  }

  const baseUrl = parseHttpsUrl(configuredUrl);
  if (!baseUrl) {
    return { ok: false, message: "H5 地址配置无效，当前版本无法启动。" };
  }

  if (baseUrl.search || baseUrl.hash) {
    return { ok: false, message: "H5 地址不得包含凭据、查询参数或片段。" };
  }

  const query = [
    ["client", "android-app"],
    ["shellVersion", __BUDLOG_SHELL_VERSION__],
    ["bridgeVersion", BRIDGE_VERSION],
  ]
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join("&");

  return {
    ok: true,
    value: {
      allowedOrigin: baseUrl.origin,
      allowedOriginPattern: `^${escapeRegularExpression(baseUrl.origin)}(?:[/?#].*)?$`,
      shellVersion: __BUDLOG_SHELL_VERSION__,
      startUrl: `${baseUrl.href}?${query}`,
    },
  };
}

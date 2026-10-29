import { clearPassword, getPassword, redirectToAccess } from "../utils/auth";
import { requestNativeWidgetRefresh } from "../utils/nativeWidget";

interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

interface RequestOptions {
  method?: HttpMethod;
  data?: unknown;
  password?: string;
  skipAuthRedirect?: boolean;
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || "/api/v1";
const DEFAULT_RETRY_AFTER_SECONDS = 60;

export class AccessVerificationError extends Error {
  constructor(
    message: string,
    readonly statusCode: number,
    readonly retryAfterSeconds = 0,
  ) {
    super(message);
    this.name = "AccessVerificationError";
  }
}

export function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const password = options.password ?? getPassword();
    uni.request({
      url: `${API_BASE}${path}`,
      method: (options.method || "GET") as UniApp.RequestOptions["method"],
      data: options.data as UniApp.RequestOptions["data"],
      header: password ? { "X-App-Password": password } : {},
      success: async (response) => {
        if (response.statusCode === 204) {
          refreshWidgetAfterMutation(path, options.method);
          resolve(undefined as T);
          return;
        }
        const payload = response.data as ApiEnvelope<T> | undefined;
        if (response.statusCode >= 200 && response.statusCode < 300 && payload?.code === 0) {
          refreshWidgetAfterMutation(path, options.method);
          resolve(payload.data);
          return;
        }
        if (response.statusCode === 401 && !options.skipAuthRedirect) {
          await clearPassword();
          redirectToAccess();
        }
        reject(new Error(payload?.message || `请求失败（${response.statusCode}）`));
      },
      fail: (error) => reject(new Error(error.errMsg || "网络连接失败")),
    });
  });
}

function refreshWidgetAfterMutation(path: string, method: HttpMethod | undefined): void {
  if (!method || method === "GET" || path === "/access/logout") return;
  requestNativeWidgetRefresh();
}

function readRetryAfterSeconds(header: Record<string, unknown>): number {
  const retryAfterEntry = Object.entries(header).find(
    ([name]) => name.toLowerCase() === "retry-after",
  );
  const parsed = Number.parseInt(String(retryAfterEntry?.[1] ?? ""), 10);
  return Number.isFinite(parsed) && parsed > 0 ? Math.min(parsed, 300) : DEFAULT_RETRY_AFTER_SECONDS;
}

export function verifyPassword(password: string): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    uni.request({
      url: `${API_BASE}/access/verify`,
      method: "POST",
      header: password ? { "X-App-Password": password } : {},
      success: (response) => {
        if (response.statusCode === 204) resolve();
        else if (response.statusCode === 401) reject(new Error("家庭访问密码不正确"));
        else if (response.statusCode === 429) {
          const retryAfterSeconds = readRetryAfterSeconds(
            (response.header || {}) as Record<string, unknown>,
          );
          reject(new AccessVerificationError(
            `请求过于频繁，请 ${retryAfterSeconds} 秒后重试`,
            response.statusCode,
            retryAfterSeconds,
          ));
        }
        else reject(new Error(`服务器暂时不可用（${response.statusCode}）`));
      },
      fail: (error) => reject(new Error(error.errMsg || "无法连接服务器")),
    });
  });
}

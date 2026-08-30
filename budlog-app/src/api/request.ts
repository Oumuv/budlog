import { clearPassword, getPassword, redirectToAccess } from "../utils/auth";

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

export function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const password = options.password ?? getPassword();
    uni.request({
      url: `${API_BASE}${path}`,
      method: (options.method || "GET") as UniApp.RequestOptions["method"],
      data: options.data as UniApp.RequestOptions["data"],
      header: password ? { "X-App-Password": password } : {},
      success: (response) => {
        if (response.statusCode === 204) {
          resolve(undefined as T);
          return;
        }
        const payload = response.data as ApiEnvelope<T> | undefined;
        if (response.statusCode >= 200 && response.statusCode < 300 && payload?.code === 0) {
          resolve(payload.data);
          return;
        }
        if (response.statusCode === 401 && !options.skipAuthRedirect) {
          clearPassword();
          redirectToAccess();
        }
        reject(new Error(payload?.message || `请求失败（${response.statusCode}）`));
      },
      fail: (error) => reject(new Error(error.errMsg || "网络连接失败")),
    });
  });
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
        else reject(new Error(`服务器暂时不可用（${response.statusCode}）`));
      },
      fail: (error) => reject(new Error(error.errMsg || "无法连接服务器")),
    });
  });
}

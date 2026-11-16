import { parseHttpsUrl } from "@/config/environment";

export interface AndroidReleaseManifest {
  apkUrl: string;
  publishedAt: string;
  releaseNotes: string;
  versionCode: number;
  versionName: string;
}

type InstalledVersion = { versionCode: number; versionName: string };

const MANIFEST_PATH = "/android/latest.json";
const CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000;
const REQUEST_TIMEOUT_MS = 10_000;
const VERSION_PROPERTY_TIMEOUT_MS = 1_500;
const DOWNLOAD_STALL_TIMEOUT_MS = 120_000;
const DOWNLOAD_WATCHDOG_INTERVAL_MS = 5_000;
const MAX_VERSION_CODE = 2_100_000_000;
const DOWNLOAD_DIRECTORY = "_downloads";
const DOWNLOAD_FAILED = "更新包下载失败，请检查网络后重试。";
const DOWNLOAD_TIMEOUT = "更新包下载超时，请检查网络后重试。";
const INSTALL_FAILED = "更新包已下载，但安装没有完成。请允许 Budlog 安装未知来源应用后重试。";
const VERSION_NAME_PATTERN = /^\d{1,4}(\.\d{1,4}){1,3}$/;
const APK_FILE_PATTERN = /\/[^/]+\.apk$/;

let lastCheckAt = 0;
let lastPromptedVersionCode = 0;
let checkInFlight: Promise<void> | null = null;
let downloading = false;
let lastProgressPercent = -1;
let progressBox: PlusNativeUIWaitingObj | null = null;

function warn(message: string, detail?: unknown): void {
  if (detail === undefined) console.warn(`Budlog Android update: ${message}`);
  else console.warn(`Budlog Android update: ${message}`, detail);
}

function reject(reason: string): null {
  warn(reason);
  return null;
}

// Relative paths resolve against the shell origin; another origin, credentials, another
// scheme, a query/fragment or a non-.apk path is rejected.
function resolveSameOriginUrl(rawValue: unknown, allowedOrigin: string): string | null {
  if (typeof rawValue !== "string") return null;
  const raw = rawValue.trim();
  if (!raw || raw.length > 512 || raw.startsWith("//")) return null;
  const parsed = parseHttpsUrl(raw.startsWith("/") ? `${allowedOrigin}${raw}` : raw);
  if (!parsed || parsed.origin !== allowedOrigin || parsed.search || parsed.hash) return null;
  return parsed.href;
}

function parseManifest(value: unknown, allowedOrigin: string): AndroidReleaseManifest | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return reject("清单结构不是 JSON 对象");

  const source = value as Record<string, unknown>;
  const { versionName, versionCode, publishedAt, releaseNotes } = source;
  if (typeof versionName !== "string" || typeof versionCode !== "number") {
    return reject("versionName/versionCode 字段类型错误");
  }
  if (typeof publishedAt !== "string" || typeof releaseNotes !== "string") {
    return reject("publishedAt/releaseNotes 字段类型错误");
  }
  if (!VERSION_NAME_PATTERN.test(versionName.trim())) return reject("versionName 不匹配安全字符集");
  if (!Number.isInteger(versionCode) || versionCode <= 0 || versionCode > MAX_VERSION_CODE) {
    return reject("versionCode 不是合法正整数");
  }
  if (!publishedAt.trim() || Number.isNaN(Date.parse(publishedAt))) return reject("publishedAt 不是合法时间");

  const apkUrl = resolveSameOriginUrl(source.apkUrl, allowedOrigin);
  if (!apkUrl || !APK_FILE_PATTERN.test(apkUrl.slice(allowedOrigin.length))) {
    return reject("apkUrl 不是同源 .apk 地址");
  }
  return {
    apkUrl,
    publishedAt: publishedAt.trim(),
    releaseNotes: releaseNotes.trim().slice(0, 120),
    versionCode,
    versionName: versionName.trim(),
  };
}

// App-Plus has no global fetch, so the manifest goes through uni.request (native network, no CORS).
function requestJson(url: string): Promise<{ ok: boolean; payload?: unknown }> {
  return new Promise((resolve) => {
    const done = (ok: boolean, payload?: unknown): void => resolve({ ok, payload });
    if (typeof uni.request !== "function") {
      warn("当前运行时不支持 uni.request，本次跳过更新检查");
      return done(false);
    }
    try {
      uni.request({
        url,
        dataType: "json",
        timeout: REQUEST_TIMEOUT_MS,
        header: { Accept: "application/json" },
        success: ({ statusCode, data }) => {
          if (statusCode < 200 || statusCode >= 300) {
            warn(`清单请求失败：HTTP ${statusCode}`);
            return done(false);
          }
          if (typeof data !== "string") return done(true, data);
          // A SPA fallback answers with HTML and HTTP 200, so a non-JSON body stays out of the parser.
          try {
            done(true, JSON.parse(data));
          } catch {
            warn("清单不是合法 JSON");
            done(false);
          }
        },
        fail: (error) => {
          warn("清单请求失败", error);
          done(false);
        },
      });
    } catch (error) {
      warn("清单请求异常", error);
      done(false);
    }
  });
}

async function fetchLatestManifest(allowedOrigin: string): Promise<AndroidReleaseManifest | null> {
  const result = await requestJson(`${allowedOrigin}${MANIFEST_PATH}?_=${Date.now()}`);
  if (!result.ok) return null;
  return parseManifest(result.payload, allowedOrigin);
}

function usableVersionCode(value: unknown): number | null {
  const code = Number(value);
  return Number.isInteger(code) && code > 0 && code <= MAX_VERSION_CODE ? code : null;
}

// The installed APK version is authoritative; missing/illegal/timeout values fall back to the injected one.
function readInstalledVersion(): Promise<InstalledVersion | null> {
  const injectedCode = usableVersionCode(__BUDLOG_SHELL_VERSION_CODE__);
  const fallback = injectedCode ? { versionCode: injectedCode, versionName: __BUDLOG_SHELL_VERSION__ } : null;
  return new Promise((resolve) => {
    let settled = false;
    const finish = (value: InstalledVersion | null): void => {
      if (settled) return;
      settled = true;
      resolve(value);
    };
    const timer = setTimeout(() => finish(fallback), VERSION_PROPERTY_TIMEOUT_MS);
    try {
      plus.runtime.getProperty(plus.runtime.appid ?? "", (info) => {
        clearTimeout(timer);
        const versionCode = usableVersionCode(info?.versionCode);
        if (!versionCode) return finish(fallback);
        finish({ versionCode, versionName: (info?.version ?? "").trim() || __BUDLOG_SHELL_VERSION__ });
      });
    } catch (error) {
      clearTimeout(timer);
      warn("读取已安装版本异常", error);
      finish(fallback);
    }
  });
}

function confirmUpdate(manifest: AndroidReleaseManifest, installedVersionName: string): Promise<boolean> {
  const notes = manifest.releaseNotes || "包含问题修复与体验优化。";
  const content = `当前版本 ${installedVersionName || "未知"}，最新版本 ${manifest.versionName}。\n${notes}`;
  return new Promise((resolve) => {
    uni.showModal({
      title: "发现新版本",
      content,
      confirmText: "立即更新",
      cancelText: "稍后",
      success: (result) => resolve(Boolean(result.confirm)),
      fail: (error) => {
        warn("更新提示弹出失败", error);
        resolve(false);
      },
    });
  });
}

function showFailure(title: string, content: string): void {
  const options = { title, content, showCancel: false, confirmText: "知道了" };
  uni.showModal({ ...options, fail: (error) => warn(`${title}提示弹出失败`, error) });
}

function setProgress(title: string, onClose?: () => void): void {
  try {
    if (progressBox) return progressBox.setTitle(title);
    progressBox = plus.nativeUI.showWaiting(title, { back: onClose ? "close" : "none" });
    if (onClose) progressBox.onclose = onClose;
  } catch (error) {
    warn("更新下载进度提示不可用", error);
  }
}

function closeProgress(): void {
  lastProgressPercent = -1;
  const box = progressBox;
  progressBox = null;
  try {
    box?.close();
  } catch {
    // The waiting box may already be closed by the system.
  }
}

// plus.downloader keeps an existing file and appends "(1)", so drop the stale copy first.
function removeExistingApk(path: string): Promise<void> {
  return new Promise((resolve) => {
    const done = (): void => resolve();
    try {
      plus.io.resolveLocalFileSystemURL(path, (entry) => entry.remove(done, done), done);
    } catch {
      done();
    }
  });
}

// plus.runtime.install expects an absolute path; keep the raw path when conversion fails.
function resolveInstallFilePath(filePath: string): string {
  try {
    return plus.io.convertLocalFileSystemURL(filePath).trim() || filePath;
  } catch (error) {
    warn("本地路径转换失败", error);
    return filePath;
  }
}

function installApk(filePath: string): void {
  const onFailure = (error: unknown): void => {
    warn("拉起系统安装器失败", error);
    showFailure("安装未完成", INSTALL_FAILED);
  };
  try {
    plus.runtime.install(resolveInstallFilePath(filePath), { force: false }, () => {
      // The system installer takes over; the shell waits for the user to finish installing.
    }, onFailure);
  } catch (error) {
    onFailure(error);
  }
}

async function startDownload(manifest: AndroidReleaseManifest): Promise<void> {
  if (downloading) {
    uni.showToast({ title: "更新包正在下载", icon: "none" });
    return;
  }
  downloading = true;
  const filename = `${DOWNLOAD_DIRECTORY}/budlog-android-${manifest.versionCode}.apk`;
  await removeExistingApk(filename);

  let finished = false;
  let aborted = false;
  let task: PlusDownloaderDownload | null = null;
  let watchdog: ReturnType<typeof setInterval> | null = null;
  let lastProgressAt = Date.now();
  // Aborts the transfer and resets the shared state: a stalled or cancelled download must not lock the next try.
  const abortDownload = (failure?: { content: string; title: string }): void => {
    if (finished) return;
    finished = true;
    aborted = true;
    if (watchdog !== null) clearInterval(watchdog);
    try { task?.abort(); } catch (error) { warn("中止下载任务失败", error); }
    downloading = false;
    closeProgress();
    if (failure) showFailure(failure.title, failure.content);
    else uni.showToast({ title: "已取消更新下载", icon: "none" });
  };
  const onStateChanged = (download: PlusDownloaderDownload): void => {
    if (finished) return;
    const total = download.totalSize ?? 0;
    const downloaded = download.downloadedSize ?? 0;
    if (total <= 0 || downloaded <= 0) return;
    lastProgressAt = Date.now();
    const percent = Math.min(99, Math.floor((downloaded / total) * 100));
    if (percent === lastProgressPercent) return;
    lastProgressPercent = percent;
    setProgress(`正在下载更新包 ${percent}%`);
  };

  try {
    task = plus.downloader.createDownload(
      manifest.apkUrl,
      { filename, retry: 1, timeout: 120 },
      (download, status) => {
        finished = true;
        if (aborted) return;
        if (watchdog !== null) clearInterval(watchdog);
        downloading = false;
        closeProgress();
        const filePath = typeof download?.filename === "string" ? download.filename : "";
        if (status !== 200 || !filePath) {
          warn(`更新包下载失败：HTTP ${status}`);
          showFailure("下载失败", DOWNLOAD_FAILED);
          return;
        }
        installApk(filePath);
      },
    );
    task.addEventListener("statechanged", onStateChanged);
    // The back key must close the box and stop the transfer, so "close" needs an onclose handler.
    setProgress("正在下载更新包", () => {
      progressBox = null;
      abortDownload();
    });
    task.start();
    // A stalled transfer never reaches the completion callback, so watch the last progress time.
    watchdog = setInterval(() => {
      if (finished || Date.now() - lastProgressAt < DOWNLOAD_STALL_TIMEOUT_MS) return;
      warn("更新包下载长时间无进度，中止任务");
      abortDownload({ title: "下载超时", content: DOWNLOAD_TIMEOUT });
    }, DOWNLOAD_WATCHDOG_INTERVAL_MS);
  } catch (error) {
    finished = true;
    downloading = false;
    closeProgress();
    warn("创建下载任务失败", error);
    showFailure("下载失败", DOWNLOAD_FAILED);
  }
}

async function performCheck(allowedOrigin: string): Promise<void> {
  try {
    const installed = await readInstalledVersion();
    if (!installed) {
      warn("无法读取已安装版本，跳过本次检查");
      return;
    }
    const manifest = await fetchLatestManifest(allowedOrigin);
    if (!manifest || manifest.versionCode <= installed.versionCode) return;
    if (manifest.versionCode === lastPromptedVersionCode) return;

    lastPromptedVersionCode = manifest.versionCode;
    if (await confirmUpdate(manifest, installed.versionName)) await startDownload(manifest);
  } catch (error) {
    warn("检查更新失败", error instanceof Error ? error.message : error);
  }
}

/**
 * Startup and foreground returns both call this; a 6 hour throttle plus the per-process
 * "last prompted" version code keep the modal from repeating within one app process.
 */
export function checkForAndroidUpdate(options: { allowedOrigin: string }): Promise<void> {
  if (typeof plus === "undefined") return Promise.resolve();
  // Normalize to the canonical origin so a trailing slash or host casing cannot break the same-origin check.
  const allowedOrigin = parseHttpsUrl(options.allowedOrigin.trim())?.origin;
  if (!allowedOrigin) return Promise.resolve();
  if (checkInFlight) return checkInFlight;

  const now = Date.now();
  if (now - lastCheckAt < CHECK_INTERVAL_MS) return Promise.resolve();
  lastCheckAt = now;

  const check = performCheck(allowedOrigin);
  checkInFlight = check;
  const clear = (): void => {
    if (checkInFlight === check) checkInFlight = null;
  };
  void check.then(clear, (error) => {
    clear();
    warn("检查更新异常", error);
  });
  return check;
}

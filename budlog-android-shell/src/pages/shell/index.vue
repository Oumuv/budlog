<script setup lang="ts">
import { computed, getCurrentInstance, nextTick, ref } from "vue";
import { onBackPress, onReady, onUnload } from "@dcloudio/uni-app";
import { BRIDGE_VERSION, parseHttpsUrl, resolveShellEnvironment } from "@/config/environment";
import {
  consumeShortcutRoute,
  dispatchNative,
  getNativeState,
  initializeNative,
} from "@/uni_modules/budlog-native";

type ShellState = "configuration-error" | "error" | "loading" | "offline" | "ready";

type RemoteWebviewStyles = PlusWebviewWebviewStyles & {
  "uni-app": "none";
};

const LOAD_TIMEOUT_MS = 20_000;
const WEBVIEW_READY_TIMEOUT_MS = 3_000;
const WEBVIEW_POLL_INTERVAL_MS = 50;
const NATIVE_STATE_POLL_INTERVAL_MS = 500;
const NATIVE_MEDIA_URL_PREFIX = "budlog-native://media?payload=";
const MAX_NATIVE_MEDIA_URL_LENGTH = 196_608;

const REMOTE_WEBVIEW_STYLES: RemoteWebviewStyles = {
  "uni-app": "none",
  backButtonAutoControl: "none",
  background: "#F7F5F4",
  cachemode: "default",
  disablePlus: true,
  errorPage: "none",
  plusrequire: "none",
  progress: {
    color: "#B94B5D",
    height: "2px",
  },
};

type PageProxyWithScope = {
  $scope?: {
    $getAppWebview?: () => PlusWebviewWebviewObject;
  };
};

type OverrideUrlResult = {
  url?: string;
};

const environmentResult = resolveShellEnvironment();
const pageInstance = getCurrentInstance();
const shellState = ref<ShellState>(environmentResult.ok ? "loading" : "configuration-error");
const stateMessage = ref(
  environmentResult.ok ? "正在连接家庭日记..." : environmentResult.message,
);
const webviewMounted = ref(false);

let activeAttempt = 0;
let loadTimeout: ReturnType<typeof setTimeout> | null = null;
let remoteWebview: PlusWebviewWebviewObject | null = null;
let backCheckWebview: PlusWebviewWebviewObject | null = null;
let nativeInitialized = false;
let nativeStateInterval: ReturnType<typeof setInterval> | null = null;
let lastNativeState = "";
let pendingShortcutRoute = "";

const stateTitle = computed(() => {
  switch (shellState.value) {
    case "configuration-error":
      return "应用配置不可用";
    case "offline":
      return "当前没有网络";
    case "error":
      return "暂时无法打开 Budlog";
    default:
      return "正在打开 Budlog";
  }
});

const canRetry = computed(
  () => shellState.value === "offline" || shellState.value === "error",
);

function clearLoadTimeout(): void {
  if (loadTimeout !== null) {
    clearTimeout(loadTimeout);
    loadTimeout = null;
  }
}

function stopNativeStatePolling(): void {
  if (nativeStateInterval === null) return;
  clearInterval(nativeStateInterval);
  nativeStateInterval = null;
}

function emitRemoteEvent(eventName: string, detailJson: string): void {
  const webview = remoteWebview;
  if (!webview || shellState.value !== "ready") return;
  try {
    JSON.parse(detailJson);
    webview.evalJS(
      `window.dispatchEvent(new CustomEvent(${JSON.stringify(eventName)},{detail:JSON.parse(${JSON.stringify(detailJson)})}));`,
    );
  } catch {
    // Native values must be valid JSON before they cross into the remote page.
  }
}

function installRemoteNativeBridge(webview: PlusWebviewWebviewObject): void {
  if (!nativeInitialized) return;
  const prefix = JSON.stringify(NATIVE_MEDIA_URL_PREFIX);
  const version = JSON.stringify(BRIDGE_VERSION);
  webview.evalJS(`(function(){var bridge=Object.freeze({version:${version},dispatch:function(payload){if(typeof payload!=="string"||payload.length===0||payload.length>65536){throw new Error("Invalid native media command.");}window.location.href=${prefix}+encodeURIComponent(payload);}});try{Object.defineProperty(window,"BudlogNativeMedia",{value:bridge,writable:false,configurable:false});}catch(e){window.BudlogNativeMedia=bridge;}window.dispatchEvent(new CustomEvent("budlog-native-ready",{detail:{version:${version}}}));})();`);
}

function pollNativeState(): void {
  if (!nativeInitialized || shellState.value !== "ready") return;
  try {
    const state = getNativeState();
    if (!state || state === lastNativeState) return;
    JSON.parse(state);
    lastNativeState = state;
    emitRemoteEvent("budlog-native-media-state", state);
  } catch {
    stopNativeStatePolling();
  }
}

function startNativeStatePolling(): void {
  if (!nativeInitialized || nativeStateInterval !== null) return;
  pollNativeState();
  nativeStateInterval = setInterval(pollNativeState, NATIVE_STATE_POLL_INTERVAL_MS);
}

function deliverPendingShortcut(): void {
  const webview = remoteWebview;
  if (!pendingShortcutRoute || !webview || shellState.value !== "ready") return;
  const route = pendingShortcutRoute;
  const routeJson = JSON.stringify(route);
  const detailJson = JSON.stringify(JSON.stringify({ route }));
  try {
    webview.evalJS(
      `(function(){var route=${routeJson};var targetHash="#"+route;if(window.location.hash===targetHash){return;}var event=new CustomEvent("budlog-native-shortcut",{detail:JSON.parse(${detailJson}),cancelable:true});var handled=!window.dispatchEvent(event);if(!handled){window.location.hash=targetHash;}})();`,
    );
    pendingShortcutRoute = "";
  } catch {
    // Keep the route queued so the next loaded event can retry delivery.
  }
}

function resolveRemoteStartUrl(): string {
  if (!environmentResult.ok || !pendingShortcutRoute) {
    return environmentResult.ok ? environmentResult.value.startUrl : "";
  }
  return `${environmentResult.value.startUrl}#${pendingShortcutRoute}`;
}

function consumeNativeShortcut(): void {
  if (!nativeInitialized) return;
  try {
    const route = consumeShortcutRoute();
    if (!route) return;
    pendingShortcutRoute = route;
    deliverPendingShortcut();
  } catch {
    // A malformed or unsupported shortcut is ignored by the native allowlist.
  }
}

function initializeNativeIntegration(): void {
  if (!environmentResult.ok) return;
  try {
    nativeInitialized = initializeNative(environmentResult.value.allowedOrigin);
    if (nativeInitialized) consumeNativeShortcut();
  } catch {
    nativeInitialized = false;
  }
}

function unmountRemoteWebview(): void {
  stopNativeStatePolling();
  lastNativeState = "";
  remoteWebview = null;
  backCheckWebview = null;
  webviewMounted.value = false;
}

function showFailure(state: "error" | "offline", message: string): void {
  clearLoadTimeout();
  unmountRemoteWebview();
  shellState.value = state;
  stateMessage.value = message;
}

function getNetworkAvailability(): Promise<boolean> {
  return new Promise((resolve) => {
    uni.getNetworkType({
      success: ({ networkType }) => resolve(networkType !== "none"),
      fail: () => resolve(false),
    });
  });
}

function notifyRejectedNavigation(): void {
  uni.showToast({
    title: "已阻止外部链接",
    icon: "none",
  });
}

function handleNativeMediaNavigation(url: string): boolean {
  if (!url.startsWith(NATIVE_MEDIA_URL_PREFIX) || url.length > MAX_NATIVE_MEDIA_URL_LENGTH) {
    return false;
  }
  if (!nativeInitialized) {
    uni.showToast({ title: "原生播放服务未就绪", icon: "none" });
    return true;
  }
  try {
    const command = decodeURIComponent(url.slice(NATIVE_MEDIA_URL_PREFIX.length));
    const result = JSON.parse(dispatchNative(command)) as { ok?: boolean; error?: string };
    if (!result.ok) {
      uni.showToast({ title: result.error || "播放操作失败", icon: "none" });
    }
  } catch {
    uni.showToast({ title: "播放操作失败", icon: "none" });
  }
  return true;
}

function handleRejectedNavigation(result: OverrideUrlResult): void {
  const url = typeof result?.url === "string" ? result.url : "";
  if (url && handleNativeMediaNavigation(url)) return;
  notifyRejectedNavigation();
}

function getPageWebview(): PlusWebviewWebviewObject {
  const pageProxy = pageInstance?.proxy as PageProxyWithScope | null;
  const pageWebview = pageProxy?.$scope?.$getAppWebview?.();
  if (!pageWebview) {
    throw new Error("Current uni-app page WebView is unavailable.");
  }
  return pageWebview;
}

function waitForWebviewPoll(): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, WEBVIEW_POLL_INTERVAL_MS);
  });
}

async function mountRemoteWebview(attempt: number): Promise<PlusWebviewWebviewObject | null> {
  const pageWebview = getPageWebview();
  const existingChildren = new Set(pageWebview.children());
  webviewMounted.value = true;
  await nextTick();

  const deadline = Date.now() + WEBVIEW_READY_TIMEOUT_MS;
  while (activeAttempt === attempt && Date.now() < deadline) {
    const webview = pageWebview
      .children()
      .find((child) => !existingChildren.has(child));
    if (webview) return webview;
    await waitForWebviewPoll();
  }

  if (activeAttempt !== attempt) {
    webviewMounted.value = false;
    await nextTick();
    return null;
  }
  throw new Error("The uni-app web-view component did not create its native child WebView.");
}

function handleRemoteLoaded(webview: PlusWebviewWebviewObject): void {
  if (
    remoteWebview !== webview
    || !environmentResult.ok
  ) return;

  const loadedUrl = parseHttpsUrl(webview.getURL());
  if (!loadedUrl || loadedUrl.origin !== environmentResult.value.allowedOrigin) return;

  if (shellState.value === "loading") {
    clearLoadTimeout();
    shellState.value = "ready";
    stateMessage.value = "";
  } else if (shellState.value !== "ready") {
    return;
  }
  installRemoteNativeBridge(webview);
  startNativeStatePolling();
  deliverPendingShortcut();
}

function handleRemoteError(webview: PlusWebviewWebviewObject): void {
  if (
    remoteWebview !== webview
    || (shellState.value !== "loading" && shellState.value !== "ready")
  ) return;
  const attempt = activeAttempt;
  void getNetworkAvailability().then((isConnected) => {
    if (activeAttempt !== attempt || remoteWebview !== webview) return;
    showFailure(
      isConnected ? "error" : "offline",
      isConnected ? "服务器暂时不可用，请稍后重试。" : "连接网络后再试一次。",
    );
  });
}

function configureRemoteWebview(webview: PlusWebviewWebviewObject, originPattern: string): void {
  // The component creates the native child first; navigation starts only after these guards exist.
  remoteWebview = webview;

  webview.overrideUrlLoading(
    {
      effect: "instant",
      exclude: "none",
      match: originPattern,
      mode: "allow",
    },
    handleRejectedNavigation,
  );

  webview.addEventListener("loaded", () => handleRemoteLoaded(webview));
  webview.addEventListener("error", () => handleRemoteError(webview));
}

async function ensureRemoteWebview(attempt: number): Promise<PlusWebviewWebviewObject | null> {
  if (remoteWebview) return remoteWebview;

  const webview = await mountRemoteWebview(attempt);
  if (!webview || activeAttempt !== attempt || !environmentResult.ok) return null;

  configureRemoteWebview(webview, environmentResult.value.allowedOriginPattern);
  return webview;
}

async function startShell(): Promise<void> {
  if (!environmentResult.ok) {
    shellState.value = "configuration-error";
    stateMessage.value = environmentResult.message;
    return;
  }

  const attempt = ++activeAttempt;
  clearLoadTimeout();
  unmountRemoteWebview();
  shellState.value = "loading";
  stateMessage.value = "正在连接家庭日记...";
  await nextTick();

  const isConnected = await getNetworkAvailability();
  if (activeAttempt !== attempt) {
    return;
  }
  if (!isConnected) {
    showFailure("offline", "连接网络后再试一次。");
    return;
  }

  try {
    const webview = await ensureRemoteWebview(attempt);
    if (!webview || activeAttempt !== attempt) return;

    loadTimeout = setTimeout(() => {
      if (activeAttempt !== attempt || remoteWebview !== webview) return;
      webview.stop();
      showFailure("error", "连接超时，请稍后重试。");
    }, LOAD_TIMEOUT_MS);
    webview.loadURL(resolveRemoteStartUrl());
  } catch {
    showFailure("error", "WebView 初始化失败，请重新打开应用。");
  }
}

function handleNetworkStatusChange(result: UniNamespace.OnNetworkStatusChangeSuccess): void {
  if (!result.isConnected && shellState.value !== "ready") {
    ++activeAttempt;
    showFailure("offline", "连接网络后再试一次。");
    return;
  }

  if (!result.isConnected) {
    uni.showToast({ title: "网络连接已断开", icon: "none" });
  }
}

function handleBackPress(): void {
  const webview = remoteWebview;
  if (!webview || shellState.value !== "ready") {
    plus.runtime.quit();
    return;
  }
  if (backCheckWebview) return;

  backCheckWebview = webview;
  try {
    webview.canBack((result: { canBack?: boolean }) => {
      if (backCheckWebview !== webview) return;
      backCheckWebview = null;
      if (remoteWebview !== webview) return;
      if (result.canBack) {
        webview.back();
        return;
      }
      plus.runtime.quit();
    });
  } catch {
    if (backCheckWebview === webview) backCheckWebview = null;
    plus.runtime.quit();
  }
}

onReady(() => {
  initializeNativeIntegration();
  plus.globalEvent.addEventListener("newintent", consumeNativeShortcut);
  uni.onNetworkStatusChange(handleNetworkStatusChange);
  void startShell();
});

onBackPress(() => {
  handleBackPress();
  return true;
});

onUnload(() => {
  ++activeAttempt;
  clearLoadTimeout();
  stopNativeStatePolling();
  plus.globalEvent.removeEventListener("newintent", consumeNativeShortcut);
  uni.offNetworkStatusChange(handleNetworkStatusChange);
  unmountRemoteWebview();
});
</script>

<template>
  <web-view
    v-if="webviewMounted"
    :src="''"
    :update-title="false"
    :webview-styles="REMOTE_WEBVIEW_STYLES"
  />
  <view class="shell-state" :class="`shell-state--${shellState}`">
    <view v-if="shellState === 'loading'" class="shell-state__spinner" aria-hidden="true" />
    <view v-else class="shell-state__mark" aria-hidden="true">
      {{ shellState === "offline" ? "!" : "i" }}
    </view>
    <text class="shell-state__title">{{ stateTitle }}</text>
    <text class="shell-state__message">{{ stateMessage }}</text>
    <button
      v-if="canRetry"
      class="shell-state__retry"
      aria-label="重新加载 Budlog"
      @click="startShell"
    >
      重新加载
    </button>
  </view>
</template>

<style scoped>
.shell-state {
  display: flex;
  width: 100%;
  min-height: 100vh;
  min-height: 100dvh;
  padding: calc(28px + env(safe-area-inset-top)) 28px calc(28px + env(safe-area-inset-bottom));
  align-items: center;
  justify-content: center;
  flex-direction: column;
  background: #f7f5f4;
  text-align: center;
}

.shell-state__spinner {
  width: 36px;
  height: 36px;
  border: 3px solid #eadde0;
  border-top-color: #b94b5d;
  border-radius: 50%;
  animation: shell-spin 0.8s linear infinite;
}

.shell-state__mark {
  display: flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border: 1px solid #dec9ce;
  border-radius: 50%;
  color: #8a3948;
  background: #f5e8eb;
  font-size: 20px;
  font-weight: 700;
}

.shell-state__title {
  display: block;
  margin-top: 18px;
  color: #202522;
  font-size: 18px;
  font-weight: 700;
  line-height: 26px;
}

.shell-state__message {
  display: block;
  max-width: 300px;
  margin-top: 8px;
  color: #68706b;
  font-size: 14px;
  line-height: 22px;
}

.shell-state__retry {
  display: flex;
  min-width: 132px;
  min-height: 44px;
  margin-top: 24px;
  padding: 0 20px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 7px;
  color: #ffffff;
  background: #a84052;
  font-size: 15px;
  font-weight: 650;
  line-height: 20px;
}

.shell-state__retry:active {
  opacity: 0.86;
}

@keyframes shell-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>

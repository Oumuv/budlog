import { computed, readonly, ref } from "vue";

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed"; platform: string }>;
}

export type PwaInstallResult = "accepted" | "dismissed" | "installed" | "manual";

const installPrompt = ref<BeforeInstallPromptEvent>();
const standalone = ref(false);
let initialized = false;

export const pwaInstallAvailable = computed(() => Boolean(installPrompt.value));
export const pwaStandalone = readonly(standalone);

function detectStandalone() {
  if (typeof window === "undefined") return false;
  const navigatorWithStandalone = window.navigator as Navigator & { standalone?: boolean };
  return window.matchMedia("(display-mode: standalone)").matches || navigatorWithStandalone.standalone === true;
}

function updateStandalone() {
  standalone.value = detectStandalone();
}

async function registerServiceWorker() {
  if (!("serviceWorker" in navigator)) return;
  try {
    await navigator.serviceWorker.register("/sw.js", { scope: "/" });
  } catch (error) {
    console.error("Budlog Service Worker registration failed", error);
  }
}

export function initializePwa() {
  if (initialized || typeof window === "undefined") return;
  initialized = true;
  updateStandalone();

  const displayMode = window.matchMedia("(display-mode: standalone)");
  displayMode.addEventListener?.("change", updateStandalone);
  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    installPrompt.value = event as BeforeInstallPromptEvent;
  });
  window.addEventListener("appinstalled", () => {
    installPrompt.value = undefined;
    updateStandalone();
  });

  if (import.meta.env.PROD) {
    if (document.readyState === "complete") void registerServiceWorker();
    else window.addEventListener("load", () => void registerServiceWorker(), { once: true });
  }
}

export async function requestPwaInstall(): Promise<PwaInstallResult> {
  if (detectStandalone()) return "installed";
  const prompt = installPrompt.value;
  if (!prompt) return "manual";

  try {
    await prompt.prompt();
    const choice = await prompt.userChoice;
    return choice.outcome;
  } finally {
    installPrompt.value = undefined;
  }
}

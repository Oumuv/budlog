/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_BUDLOG_H5_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare const __BUDLOG_SHELL_VERSION__: string;

interface PlusGlobalEvent {
  addEventListener(event: "newintent", listener: () => void): void;
  removeEventListener(event: "newintent", listener: () => void): void;
}

interface Plus {
  readonly globalEvent: PlusGlobalEvent;
}

declare module "@/uni_modules/budlog-native" {
  export function initializeNative(allowedOrigin: string): boolean;
  export function dispatchNative(commandJson: string): string;
  export function getNativeState(): string;
  export function consumeShortcutRoute(): string;
}

declare module "*.vue" {
  import type { DefineComponent } from "vue";

  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>;
  export default component;
}

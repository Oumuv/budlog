/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_BUDLOG_H5_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare const __BUDLOG_SHELL_VERSION__: string;

declare module "*.vue" {
  import type { DefineComponent } from "vue";

  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>;
  export default component;
}

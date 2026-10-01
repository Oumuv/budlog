/// <reference types="vite/client" />

declare const __BUDLOG_BUILD_ID__: string;
declare const __BUDLOG_BUILT_AT__: string;

interface BudlogNativeMediaBridge {
  readonly version: string;
  dispatch(payload: string): void;
}

interface Window {
  readonly BudlogNativeMedia?: BudlogNativeMediaBridge;
}

declare module '*.vue' {
  import { DefineComponent } from 'vue'
  // eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/ban-types
  const component: DefineComponent<{}, {}, any>
  export default component
}

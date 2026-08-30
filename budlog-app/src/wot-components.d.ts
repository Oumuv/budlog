import type { DefineComponent } from "vue";

type WotComponent = DefineComponent<any, any, any>;

declare module "vue" {
  export interface GlobalComponents {
    WdButton: WotComponent;
    WdCalendar: WotComponent;
    WdDatetimePicker: WotComponent;
    WdInput: WotComponent;
    WdInputNumber: WotComponent;
    WdLoading: WotComponent;
    WdPicker: WotComponent;
    WdSegmented: WotComponent;
    WdSwitch: WotComponent;
    WdTabbar: WotComponent;
    WdTabbarItem: WotComponent;
    WdTag: WotComponent;
    WdTextarea: WotComponent;
  }
}

export {};

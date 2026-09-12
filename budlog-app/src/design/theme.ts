import type { GlobalThemeOverrides } from "naive-ui";

export const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: "#ff4f87",
    primaryColorHover: "#ff6797",
    primaryColorPressed: "#e73c74",
    primaryColorSuppl: "#ff6797",
    infoColor: "#3888f8",
    successColor: "#32bd83",
    warningColor: "#f3a21a",
    errorColor: "#e84d6f",
    bodyColor: "#f5f8fc",
    cardColor: "#ffffff",
    modalColor: "#ffffff",
    popoverColor: "#ffffff",
    inputColor: "#ffffff",
    textColorBase: "#111a35",
    textColor1: "#111a35",
    textColor2: "#63708a",
    textColor3: "#929db2",
    borderColor: "#e9eef5",
    dividerColor: "#eef2f7",
    borderRadius: "8px",
    fontSize: "15px",
  },
  Button: {
    borderRadiusMedium: "8px",
    borderRadiusLarge: "8px",
    heightMedium: "42px",
    heightLarge: "48px",
    fontWeight: "700",
  },
  Input: {
    borderRadius: "8px",
    heightMedium: "46px",
  },
  InputNumber: {
    borderRadius: "8px",
    heightMedium: "46px",
  },
  DatePicker: {
    itemBorderRadius: "6px",
  },
};

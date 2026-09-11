import type { GlobalThemeOverrides } from "naive-ui";

export const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: "#ff5d8f",
    primaryColorHover: "#ff759f",
    primaryColorPressed: "#e9477b",
    primaryColorSuppl: "#ff759f",
    infoColor: "#4d8dff",
    successColor: "#3dbb70",
    warningColor: "#e5a51f",
    errorColor: "#d9465f",
    bodyColor: "#f7f9fc",
    cardColor: "#ffffff",
    modalColor: "#ffffff",
    popoverColor: "#ffffff",
    inputColor: "#ffffff",
    textColorBase: "#202533",
    textColor1: "#202533",
    textColor2: "#6f7787",
    textColor3: "#929bad",
    borderColor: "#e8edf4",
    dividerColor: "#eef1f5",
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

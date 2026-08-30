const STORAGE_KEY = "budlog.access.password";

let sessionPassword = "";

export function getPassword(): string {
  return sessionPassword || String(uni.getStorageSync(STORAGE_KEY) || "");
}

export function setPassword(password: string, remember: boolean): void {
  sessionPassword = password;
  if (remember) uni.setStorageSync(STORAGE_KEY, password);
  else uni.removeStorageSync(STORAGE_KEY);
}

export function clearPassword(): void {
  sessionPassword = "";
  uni.removeStorageSync(STORAGE_KEY);
}

export function redirectToAccess(): void {
  const pages = getCurrentPages();
  const current = pages.length ? pages[pages.length - 1].route : "";
  if (current !== "pages/access/access") uni.reLaunch({ url: "/pages/access/access" });
}


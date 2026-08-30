import { verifyPassword } from "../api/request";
import { clearPassword, getPassword, redirectToAccess } from "./auth";

let verifiedPassword = "";
let passwordlessAccessVerified = false;

export async function ensureAccess(): Promise<boolean> {
  const password = getPassword();
  if (!password) {
    if (passwordlessAccessVerified) return true;
    try {
      await verifyPassword("");
      passwordlessAccessVerified = true;
      return true;
    } catch {
      redirectToAccess();
      return false;
    }
  }
  if (verifiedPassword === password) return true;
  try {
    await verifyPassword(password);
    verifiedPassword = password;
    return true;
  } catch {
    clearPassword();
    verifiedPassword = "";
    redirectToAccess();
    return false;
  }
}

export function resetAccessVerification(): void {
  verifiedPassword = "";
  passwordlessAccessVerified = false;
}

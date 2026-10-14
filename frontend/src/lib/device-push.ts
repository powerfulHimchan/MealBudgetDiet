import { mutation } from "./api";

export const PUSH_STORAGE_KEY = "meal-budget-diet-push-subscription";

export async function stopDevicePushBeforeLogout() {
  if (!("serviceWorker" in navigator) || !("PushManager" in window)) return;
  const registration = await navigator.serviceWorker.getRegistration("/");
  const subscription = await registration?.pushManager.getSubscription();
  if (subscription) {
    // false means it was already deactivated, which is also safe for logout.
    await subscription.unsubscribe();
  }
  // Unsubscribing stops delivery even if the server cannot be reached. Do not
  // leave another account's notifications on a shared device after logout.
  try {
    const saved = JSON.parse(localStorage.getItem(PUSH_STORAGE_KEY) ?? "null") as { id?: string; endpoint?: string } | null;
    if (saved?.id && saved.endpoint === subscription?.endpoint) {
      await mutation(`/api/v1/push-subscriptions/${encodeURIComponent(saved.id)}`, "DELETE");
    }
  } catch {
    // The push provider will expire any remaining server-side subscription.
  }
  localStorage.removeItem(PUSH_STORAGE_KEY);
}

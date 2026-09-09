const CACHE_NAME = "meal-budget-diet-shell-v4";
const OFFLINE_URL = "/offline";

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll([OFFLINE_URL, "/icon.svg"])));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))));
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  if (event.request.mode !== "navigate") return;
  event.respondWith(fetch(event.request).catch(() => caches.match(OFFLINE_URL)));
});

self.addEventListener("push", (event) => {
  let payload = {};

  if (event.data) {
    try {
      payload = event.data.json();
    } catch {
      payload = { body: event.data.text() };
    }
  }

  event.waitUntil((async () => {
    const tag = payload.notificationId ?? payload.type ?? "meal-budget-diet";
    const cache = await caches.open(CACHE_NAME);
    const marker = new Request(`/__push_notifications__/${encodeURIComponent(tag)}`);
    if (await cache.match(marker)) return;
    const existing = await self.registration.getNotifications({ tag });
    if (existing.length > 0) return;
    await self.registration.showNotification(payload.title ?? "MealBudgetDiet", {
      body: payload.body ?? "새로운 알림이 도착했습니다.",
      icon: "/icon.svg",
      badge: "/icon.svg",
      tag,
      data: payload.data ?? { url: "/" },
    });
    await cache.put(marker, new Response("shown", { headers: { "Content-Type": "text/plain" } }));
  })());
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const targetUrl = event.notification.data?.url ?? "/";
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    for (const client of windows) {
      if ("navigate" in client) await client.navigate(targetUrl);
      if ("focus" in client) return client.focus();
    }
    return self.clients.openWindow(targetUrl);
  })());
});

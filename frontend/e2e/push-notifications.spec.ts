import { expect, test, type Page } from "@playwright/test";

async function setup(page: Page, options: { existing?: boolean; denied?: boolean; registerFailure?: boolean } = {}) {
  await page.context().addCookies([{ name: "MBD_SESSION", value: "push-test", url: "http://127.0.0.1:3000" }]);
  await page.addInitScript((options) => {
    let existing = options.existing ?? false;
    let fail = options.registerFailure ?? false;
    const subscription = {
      endpoint: "https://fcm.googleapis.com/fcm/send/test-device",
      toJSON: () => ({ endpoint: subscription.endpoint, keys: { p256dh: "key", auth: "auth" } }),
      unsubscribe: async () => { existing = false; localStorage.setItem("test-unsubscribed", "yes"); return true; },
    };
    const registration = { pushManager: {
      getSubscription: async () => existing ? subscription : null,
      subscribe: async () => { existing = true; return subscription; },
    } };
    Object.defineProperty(navigator, "serviceWorker", { value: {
      register: async (_url: string, settings?: { updateViaCache?: string }) => { if (fail && settings?.updateViaCache === "none") { fail = false; throw new Error("알림 준비 실패"); } return registration; },
      ready: Promise.resolve(registration),
      getRegistration: async () => registration,
    }, configurable: true });
    Object.defineProperty(window, "PushManager", { value: class {}, configurable: true });
    Object.defineProperty(window, "Notification", { value: {
      permission: options.denied ? "denied" : "granted",
      requestPermission: async () => options.denied ? "denied" : "granted",
    }, configurable: true });
  }, options);
  await page.route("**/api/v1/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/v1/auth/me") return route.fulfill({ json: { id: "user", displayName: "테스트", email: "test@example.com", ledgerRole: "ADMIN", profileImageUrl: null } });
    if (path === "/api/v1/ledger") return route.fulfill({ json: { name: "식비", pushUsageThreshold: 80, currentUserRole: "ADMIN", version: 0 } });
    if (path === "/api/v1/members") return route.fulfill({ json: { items: [] } });
    if (path === "/api/v1/auth/csrf") return route.fulfill({ json: { headerName: "X-XSRF-TOKEN", token: "test-csrf" } });
    if (path === "/api/v1/push/vapid-public-key") return route.fulfill({ json: { publicKey: "BAECAwQFBgcICQ" } });
    if (path === "/api/v1/push-subscriptions") return route.fulfill({ json: { id: "device", status: "ACTIVE" } });
    return route.fulfill({ status: 204 });
  });
}

test("activates a device and sends a test without changing the budget", async ({ page }) => {
  await setup(page);
  let sent = 0;
  await page.route("**/push-subscriptions/device/test", async (route) => {
    expect(route.request().method()).toBe("POST");
    expect(route.request().headers()["x-xsrf-token"]).toBe("test-csrf");
    sent++;
    return route.fulfill({ status: 202 });
  });
  await page.setViewportSize({ width: 320, height: 900 });
  await page.goto("/settings/notifications");
  await page.getByRole("button", { name: "이 기기 알림 활성화" }).click();
  await expect(page.locator(".push-state")).toHaveText("활성");
  await page.getByRole("button", { name: "테스트 알림 보내기" }).click();
  await expect(page.getByRole("status")).toContainText("기기 알림창에서 수신 여부를 확인");
  expect(sent).toBe(1);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole("button", { name: "이 기기 알림 해제" }).click();
  await expect(page.locator(".push-state")).toHaveText("비활성");
  expect(await page.evaluate(() => localStorage.getItem("test-unsubscribed"))).toBe("yes");
});

test("does not show active after a failed server sync and can retry", async ({ page }) => {
  await setup(page, { existing: true });
  let attempts = 0;
  await page.route("**/api/v1/push-subscriptions", (route) => {
    attempts++;
    return route.fulfill(attempts === 1 ? { status: 503, json: { detail: "일시 오류" } } : { json: { id: "device", status: "ACTIVE" } });
  });
  await page.goto("/settings/notifications");
  await expect(page.locator(".push-state")).toHaveText("연결 확인 필요");
  await expect(page.getByRole("button", { name: "테스트 알림 보내기" })).toHaveCount(0);
  await page.getByRole("button", { name: "알림 연결 다시 시도" }).click();
  await expect(page.locator(".push-state")).toHaveText("활성");
});

test("can retry service worker errors instead of reporting unsupported", async ({ page }) => {
  await setup(page, { registerFailure: true });
  await page.goto("/settings/notifications");
  await expect(page.getByRole("status")).toHaveText("알림 준비 실패");
  await page.getByRole("button", { name: "알림 연결 다시 시도" }).click();
  await expect(page.locator(".push-state")).toHaveText("활성");
});

test("explains Android permission recovery", async ({ page }) => {
  await setup(page, { denied: true });
  await page.goto("/settings/notifications");
  await expect(page.getByText("Android에서는 휴대폰 설정", { exact: false })).toBeVisible();
  await expect(page.getByRole("button", { name: "이 기기 알림 활성화" })).toBeDisabled();
});

test("displays provider failures without claiming device receipt", async ({ page }) => {
  await setup(page, { existing: true });
  await page.route("**/push-subscriptions/device/test", (route) => route.fulfill({ status: 429, json: { detail: "테스트 알림은 1분에 한 번 보낼 수 있습니다." } }));
  await page.goto("/settings/notifications");
  await page.getByRole("button", { name: "테스트 알림 보내기" }).click();
  await expect(page.getByRole("status")).toHaveText("테스트 알림은 1분에 한 번 보낼 수 있습니다.");
});

test("stops device delivery before logging out", async ({ page }) => {
  await setup(page, { existing: true });
  let loggedOut = false;
  await page.route("**/api/v1/auth/logout", async (route) => {
    expect(await page.evaluate(() => localStorage.getItem("test-unsubscribed"))).toBe("yes");
    loggedOut = true;
    return route.fulfill({ status: 204 });
  });
  await page.goto("/settings/account");
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect.poll(() => loggedOut).toBe(true);
});

test("asset links fail closed until a signing certificate is configured", async ({ request }) => {
  const response = await request.get("/.well-known/assetlinks.json");
  expect(response.status()).toBe(503);
  expect(await response.json()).toEqual({ code: "ANDROID_APP_LINKS_NOT_CONFIGURED" });
});

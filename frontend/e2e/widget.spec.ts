import { expect, test, type Page } from "@playwright/test";

async function mock(page: Page) {
  await page.context().addCookies([{ name: "MBD_SESSION", value: "widget-test", url: "http://127.0.0.1:3000" }]);
  const writes: { method: string; body: unknown }[] = [];
  await page.route("**/api/v1/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/v1/auth/me") return route.fulfill({ json: { displayName: "힘찬", profileImageUrl: null } });
    if (path === "/api/v1/auth/csrf") return route.fulfill({ json: { headerName: "X-XSRF-TOKEN", token: "widget-csrf" } });
    if (path === "/api/v1/widget/connections") {
      expect(route.request().headers()["x-xsrf-token"]).toBe("widget-csrf");
      writes.push({ method: route.request().method(), body: route.request().postDataJSON() });
    }
    return route.fulfill({ status: 204 });
  });
  return writes;
}

for (const width of [390, 1280]) {
  test(`approves only the device challenge and offers a credential-free callback at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const writes = await mock(page);
    const challenge = "a".repeat(64);
    await page.goto(`/settings/widget?challenge=${challenge}`);
    await page.getByRole("button", { name: "이 장부를 위젯에 연결" }).click();
    await expect(page.getByRole("link", { name: "앱으로 돌아가 완료" })).toHaveAttribute("href",
      `intent://connected?challenge=${challenge}#Intent;scheme=sikbi-widget;package=app.sikbi;end`);
    expect(writes).toEqual([{ method: "POST", body: { challenge } }]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    if (width === 390) await page.screenshot({ path: "test-results/widget-connected-mobile.png", fullPage: true });
    await page.getByRole("button", { name: "위젯 연결 해제" }).click();
    await expect(page.getByRole("status")).toContainText("모두 해제했습니다");
    expect(writes[1]).toEqual({ method: "DELETE", body: null });
  });
}

test("invalid challenge cannot authorize and login preserves the device challenge", async ({ page }) => {
  const writes = await mock(page);
  await page.goto("/settings/widget?challenge=invalid");
  await expect(page.getByRole("alert").filter({ hasText: "연결 주소" })).toContainText("연결 주소가 올바르지 않습니다");
  await expect(page.getByRole("button", { name: "이 장부를 위젯에 연결" })).toHaveCount(0);
  expect(writes).toEqual([]);
  await page.context().clearCookies();
  const next = `/settings/widget?challenge=${"f".repeat(64)}`;
  await page.goto(next);
  await expect(page).toHaveURL(/\/login\?next=/);
  expect(new URL(page.url()).searchParams.get("next")).toBe(next);
});

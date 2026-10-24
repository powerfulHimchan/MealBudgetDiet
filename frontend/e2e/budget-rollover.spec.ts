import { expect, test, type Page } from "@playwright/test";

async function mock(page: Page, enabled = false, admin = true, debt = false) {
  await page.context().addCookies([{ name: "MBD_SESSION", value: "rollover-test", url: "http://127.0.0.1:3000" }]);
  const rollover = { enabled, baseAmount: 800000, carryAmount: debt ? -900000 : enabled ? 100000 : 0, effectiveAmount: debt ? -100000 : enabled ? 900000 : 800000 };
  const ledger = { id: "ledger", name: "식비", defaultMonthlyBudget: 800000, budgetCycleStartDay: 1,
    budgetCycleUnit: "MONTHLY", budgetWeekStartDay: 1, defaultWeeklyBudget: null, memberCount: 2,
    currentUserRole: admin ? "ADMIN" : "MEMBER", version: 0 };
  let settings = { carrySurplus: enabled, carryDeficit: enabled, effectiveFrom: "2026-11-01", pending: false, version: 0 };
  const writes: unknown[] = [];
  await page.route("**/api/v1/**", async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/v1/auth/me") return route.fulfill({ json: { displayName: "힘찬", profileImageUrl: null } });
    if (path === "/api/v1/auth/csrf") return route.fulfill({ json: { headerName: "X-XSRF-TOKEN", token: "test" } });
    if (path === "/api/v1/ledger") return route.fulfill({ json: ledger });
    if (path === "/api/v1/budgets/rollover") {
      if (route.request().method() === "PUT") {
        const data = route.request().postDataJSON();
        writes.push(data);
        settings = { ...settings, ...data, version: settings.version + 1, pending: true };
        ledger.version = settings.version;
      }
      return route.fulfill({ json: settings });
    }
    if (path.startsWith("/api/v1/budgets/")) return route.fulfill({ json: {
      yearMonth: "2026-10", period: { from: "2026-10-01", to: "2026-10-31" }, amount: 800000,
      source: "DEFAULT", version: 0, rollover,
    } });
    if (path === "/api/v1/dashboard") return route.fulfill({ json: {
      yearMonth: "2026-10", cycleUnit: "MONTHLY", period: { from: "2026-10-01", to: "2026-10-31" },
      budget: rollover.effectiveAmount, rollover, spent: 100000, remaining: rollover.effectiveAmount - 100000,
      projectedSpent: 150000, remainingDays: 7, usageRate: debt ? 100 : 12.5, pushUsageThreshold: 80,
      status: debt ? "EXCEEDED" : "NORMAL", recentExpenses: [],
    } });
    if (path === "/api/v1/statistics/initial") return route.fulfill({ json: { cycleSettings: ledger, statistics: {
      period: { from: "2026-10-01", to: "2026-10-31" }, totalAmount: 100000,
      budget: { amount: rollover.effectiveAmount, usageRate: 12.5, rollover }, daily: [], categories: [],
      comparison: { from: "2026-09-01", to: "2026-09-30", totalAmount: 100000, changeAmount: 0, changeRate: 0, available: true },
    } } });
    return route.fulfill({ status: 204 });
  });
  return { writes };
}

for (const width of [390, 1280]) {
  test(`no rollover breakdown on home, statistics or applied budget when disabled at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mock(page);
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "700,000원 남았어요" })).toBeVisible();
    await expect(page.getByLabel("예산 이월 내역")).toHaveCount(0);
    await page.goto("/statistics");
    await expect(page.getByText("기간 예산", { exact: true })).toBeVisible();
    await expect(page.getByLabel("예산 이월 내역")).toHaveCount(0);
    await page.goto("/settings/budget");
    await expect(page.getByRole("heading", { name: "예산 이월", exact: true })).toBeVisible();
    await expect(page.getByLabel("남은 금액 이월")).not.toBeChecked();
    await expect(page.getByLabel("초과 금액 이월")).not.toBeChecked();
    await expect(page.getByLabel("예산 이월 내역")).toHaveCount(0);
    await expect(page.locator(".budget-guide")).toContainText("800,000원");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });

  test(`saves separate rollover settings for the next cycle and preserves them on reload at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const { writes } = await mock(page);
    await page.goto("/settings/budget");
    await page.getByLabel("남은 금액 이월").check();
    await page.getByRole("button", { name: "이월 설정 저장" }).click();
    await expect(page.getByRole("button", { name: /2026-11-01부터 적용됩니다/ })).toBeVisible();
    expect(writes).toEqual([{ carrySurplus: true, carryDeficit: false, version: 0 }]);
    await expect(page.getByLabel("예산 이월 내역")).toHaveCount(0);
    await page.reload();
    await expect(page.getByLabel("남은 금액 이월")).toBeChecked();
    await expect(page.getByLabel("초과 금액 이월")).not.toBeChecked();
    await expect(page.getByText("저장된 설정 적용 시작일: 2026-11-01")).toBeVisible();
    await page.getByLabel("초과 금액 이월").check();
    await page.getByRole("button", { name: "이월 설정 저장" }).click();
    await expect.poll(() => writes.length).toBe(2);
    expect(writes[1]).toEqual({ carrySurplus: true, carryDeficit: true, version: 1 });
  });
}

test("shows applied rollover on home, statistics and budget settings", async ({ page }) => {
  await mock(page, true);
  for (const path of ["/", "/statistics", "/settings/budget"]) {
    await page.goto(path);
    const details = page.getByLabel("예산 이월 내역");
    await expect(details).toContainText("기본 예산");
    await expect(details).toContainText("800,000원");
    await expect(details).toContainText("+100,000원");
  }
});

test("shows unavailable budget and keeps debt visible", async ({ page }) => {
  await mock(page, true, true, true);
  await page.goto("/");
  await expect(page.getByLabel("예산 이월 내역")).toContainText("초과 이월금 100,000원이 남아 있어요");
  await expect(page.getByRole("heading", { name: "200,000원 초과했어요" })).toBeVisible();
  await expect(page.locator(".budget-stats")).toContainText("전체 예산0원");
});

test("members can read settings but cannot change them", async ({ page }) => {
  const { writes } = await mock(page, true, false);
  await page.goto("/settings/budget");
  await expect(page.getByLabel("남은 금액 이월")).toBeDisabled();
  await expect(page.getByLabel("초과 금액 이월")).toBeDisabled();
  await expect(page.getByRole("button", { name: "이월 설정 저장" })).toHaveCount(0);
  expect(writes).toEqual([]);
});

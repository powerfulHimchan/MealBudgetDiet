import { expect, test, type Page } from "@playwright/test";
import { aggregateSpendingTrend, automaticSpendingTrendUnit, spendingTrendTicks } from "../src/lib/spending-trend";

test("selects automatic units using inclusive calendar days", () => {
  for (const [from, to, unit] of [
    ["2026-01-01", "2026-01-31", "daily"],
    ["2026-01-01", "2026-02-01", "weekly"],
    ["2026-01-01", "2026-06-29", "weekly"],
    ["2026-01-01", "2026-06-30", "monthly"],
    ["2024-02-01", "2024-03-02", "daily"],
    ["2024-02-01", "2024-03-03", "weekly"],
  ]) expect(automaticSpendingTrendUnit({ from, to })).toBe(unit);
});

test("clips calendar months to the queried period and preserves their exact sums", () => {
  const period = { from: "2025-12-20", to: "2026-02-05" };
  const points = aggregateSpendingTrend([
    { date: "2025-12-19", amount: 99999 },
    { date: "2026-02-01", amount: 4000 },
    { date: "2025-12-31", amount: 1000 },
    { date: "2026-01-01", amount: 2000 },
    { date: "2026-01-31", amount: 3000 },
    { date: "2026-02-06", amount: 99999 },
  ], period, "monthly");
  expect(points).toEqual([
    { from: "2025-12-20", to: "2025-12-31", amount: 1000 },
    { from: "2026-01-01", to: "2026-01-31", amount: 5000 },
    { from: "2026-02-01", to: "2026-02-05", amount: 4000 },
  ]);
});

test("uses the ledger week start across year boundaries and clips partial weeks", () => {
  const daily = [
    { date: "2025-12-31", amount: 1 }, { date: "2026-01-03", amount: 2 },
    { date: "2026-01-04", amount: 4 }, { date: "2026-01-07", amount: 8 },
  ];
  const period = { from: "2025-12-31", to: "2026-01-07" };
  expect(aggregateSpendingTrend(daily, period, "weekly", 1)).toEqual([
    { from: "2025-12-31", to: "2026-01-04", amount: 7 },
    { from: "2026-01-05", to: "2026-01-07", amount: 8 },
  ]);
  expect(aggregateSpendingTrend(daily, period, "weekly", 7)).toEqual([
    { from: "2025-12-31", to: "2026-01-03", amount: 3 },
    { from: "2026-01-04", to: "2026-01-07", amount: 12 },
  ]);
});

test("fills missing dates with zero, includes leap day and sums duplicate daily rows", () => {
  const period = { from: "2024-02-28", to: "2024-03-02" };
  const daily = [{ date: "2024-02-29", amount: 2000 }, { date: "2024-02-29", amount: 900 }, { date: "2024-03-01", amount: 1000 }];
  expect(aggregateSpendingTrend(daily, period, "daily").map((point) => point.amount)).toEqual([0, 2900, 1000, 0]);
  expect(aggregateSpendingTrend(daily, period, "monthly")).toEqual([
    { from: "2024-02-28", to: "2024-02-29", amount: 2900 },
    { from: "2024-03-01", to: "2024-03-02", amount: 1000 },
  ]);
  for (const unit of ["daily", "weekly", "monthly"] as const) {
    expect(aggregateSpendingTrend(daily, period, unit).reduce((sum, point) => sum + point.amount, 0)).toBe(3900);
  }
});

test("limits date ticks while retaining both ends of a long range", () => {
  const points = aggregateSpendingTrend([], { from: "2026-01-01", to: "2026-08-31" }, "daily");
  for (const limit of [4, 6]) {
    const ticks = spendingTrendTicks(points, limit);
    expect(ticks).toHaveLength(limit);
    expect(ticks[0]).toBe("2026-01-01");
    expect(ticks.at(-1)).toBe("2026-08-31");
    expect(new Set(ticks).size).toBe(limit);
  }
});

async function mockStatistics(page: Page, period = { from: "2026-01-15", to: "2026-08-05" }) {
  await page.context().addCookies([{ name: "MBD_SESSION", value: "trend-test", url: "http://127.0.0.1:3000" }]);
  const daily = [
    { date: "2026-01-15", amount: 1000 }, { date: "2026-01-31", amount: 2000 },
    { date: "2026-02-01", amount: 4000 }, { date: "2026-07-02", amount: 5000 }, { date: "2026-08-05", amount: 8000 },
  ];
  const requests: string[] = [];
  function statistics(range: { from: string; to: string }) {
    const selected = daily.filter((item) => item.date >= range.from && item.date <= range.to);
    return { period: range, daily: selected, totalAmount: selected.reduce((sum, item) => sum + item.amount, 0),
      budget: { amount: 800000, usageRate: 2.5 }, categories: [],
      comparison: { from: "2025-01-15", to: "2025-08-05", totalAmount: 0, changeAmount: 20000, changeRate: null },
      spendingAnalysis: { eligible: false, status: "UNAVAILABLE", text: null, analyzedAt: null } };
  }
  await page.route("**/api/v1/**", (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.startsWith("/api/v1/statistics")) requests.push(url.pathname);
    if (url.pathname === "/api/v1/statistics/initial") return route.fulfill({ json: {
      cycleSettings: { budgetCycleUnit: "MONTHLY", budgetCycleStartDay: 1, budgetWeekStartDay: 1 }, statistics: statistics(period),
    } });
    if (url.pathname === "/api/v1/statistics") return route.fulfill({ json: statistics({ from: url.searchParams.get("from")!, to: url.searchParams.get("to")! }) });
    if (url.pathname === "/api/v1/auth/me") return route.fulfill({ json: { id: "test-user", displayName: "테스트", profileImageUrl: null } });
    return route.fulfill({ status: 204 });
  });
  return requests;
}

for (const width of [320, 390, 1024]) {
  test(`selects monthly by default and allows manual units at ${width}px`, async ({ browser }) => {
    const page = await browser.newPage({ viewport: { width, height: 900 }, hasTouch: width < 600 });
    const requests = await mockStatistics(page);
    await page.goto("/statistics");
    const controls = page.getByRole("group", { name: "지출 추이 표시 단위", exact: true });
    await expect(page.getByRole("heading", { name: "월별 지출 추이", exact: true })).toBeVisible();
    await expect(controls.getByRole("button")).toHaveCount(3);
    await expect(controls.getByRole("button", { name: "자동", exact: true })).toHaveCount(0);
    await expect(controls.getByRole("button", { name: "월별", exact: true })).toHaveAttribute("aria-pressed", "true");
    const chart = page.getByRole("img", { name: "월별 지출 추이 차트", exact: true });
    await expect(chart).toHaveAccessibleDescription(/8개 월별 구간/);
    await expect(chart.locator(".recharts-area")).toBeVisible();
    await chart.scrollIntoViewIfNeeded();
    const tick = await chart.locator(".recharts-xAxis-tick-labels text").first().boundingBox();
    const box = (await chart.boundingBox())!;
    const x = tick!.x + tick!.width / 2;
    const y = box.y + box.height / 2;
    if (width < 600) await page.touchscreen.tap(x, y);
    else await page.mouse.move(x, y);
    await expect(page.getByRole("tooltip")).toContainText("2026-01-15 ~ 2026-01-31");
    await expect(page.getByRole("tooltip")).toContainText("지출 합계 3,000원");
    expect(await chart.locator(".recharts-xAxis-tick-labels text").count()).toBeLessThanOrEqual(width < 600 ? 4 : 6);

    await controls.getByRole("button", { name: "일별", exact: true }).click();
    await expect(page.getByRole("img", { name: "일별 지출 추이 차트", exact: true })).toHaveAccessibleDescription(/203개 일별 구간/);
    await controls.getByRole("button", { name: "주별", exact: true }).click();
    await expect(page.getByRole("heading", { name: "주별 지출 추이", exact: true })).toBeVisible();
    await controls.getByRole("button", { name: "월별", exact: true }).click();
    await expect(controls.getByRole("button", { name: "월별", exact: true })).toHaveAttribute("aria-pressed", "true");
    await expect(page.getByRole("heading", { name: "월별 지출 추이", exact: true })).toBeVisible();
    expect(requests).toEqual(["/api/v1/statistics/initial"]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `artifacts/screenshots/spending-trend-${width}.png`, fullPage: true });
    await page.close();
  });
}

test("resets manual units to the period default when the queried period changes", async ({ page }) => {
  await mockStatistics(page);
  await page.goto("/statistics");
  await expect(page.getByRole("heading", { name: "월별 지출 추이", exact: true })).toBeVisible();
  const controls = page.getByRole("group", { name: "지출 추이 표시 단위", exact: true });
  await controls.getByRole("button", { name: "주별", exact: true }).click();
  await page.getByRole("button", { name: "현재 주기", exact: true }).click();
  await expect(page.getByRole("heading", { name: "일별 지출 추이", exact: true })).toBeVisible();
  await expect(controls.getByRole("button", { name: "일별", exact: true })).toHaveAttribute("aria-pressed", "true");
  await controls.getByRole("button", { name: "월별", exact: true }).click();
  await page.getByRole("button", { name: "최근 3개 주기", exact: true }).click();
  await expect(page.getByRole("heading", { name: "주별 지출 추이", exact: true })).toBeVisible();
  await expect(controls.getByRole("button", { name: "주별", exact: true })).toHaveAttribute("aria-pressed", "true");
  await controls.getByRole("button", { name: "일별", exact: true }).click();
  await page.getByRole("button", { name: "올해", exact: true }).click();
  await expect(controls.getByRole("button", { name: "월별", exact: true })).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "현재 주기", exact: true }).click();
  await expect(controls.getByRole("button", { name: "일별", exact: true })).toHaveAttribute("aria-pressed", "true");
});

import { expect, test, type Page } from "@playwright/test";

async function mockPaginatedExpenses(page: Page, count = 65) {
  await page.context().addCookies([{ name: "MBD_SESSION", value: "pagination-test", url: "http://127.0.0.1:3000" }]);
  const category = { id: "11111111-1111-1111-1111-111111111111", name: "장보기", sortOrder: 1, version: 0 };
  const expenses = Array.from({ length: count }, (_, index) => ({
    id: `expense-${index + 1}`, amount: 1000, spentOn: "2026-10-15", category,
    merchant: `상호 ${String(index + 1).padStart(3, "0")}`, memo: null as string | null, images: [], version: 0,
    createdAt: new Date(Date.UTC(2026, 9, 15, 12) - index * 1000).toISOString(),
    updatedAt: "2026-10-15T12:00:00Z",
  }));
  const requests: URL[] = [];
  const ledger = { budgetCycleUnit: "MONTHLY", budgetCycleStartDay: 1, budgetWeekStartDay: 1 };
  function expensePage(url: URL) {
    const filtered = expenses.filter((expense) =>
      (!url.searchParams.get("from") || expense.spentOn >= url.searchParams.get("from")!) &&
      (!url.searchParams.get("to") || expense.spentOn <= url.searchParams.get("to")!) &&
      (!url.searchParams.get("keyword") || expense.merchant.includes(url.searchParams.get("keyword")!)),
    ).sort((a, b) => b.spentOn.localeCompare(a.spentOn) || b.createdAt.localeCompare(a.createdAt));
    const start = Number(url.searchParams.get("cursor") ?? 0);
    const size = Number(url.searchParams.get("size") ?? 20);
    expect(size).toBeLessThanOrEqual(100);
    const items = filtered.slice(start, start + size);
    const hasNext = start + items.length < filtered.length;
    return { items, nextCursor: hasNext ? String(start + items.length) : null, hasNext,
      totalCount: filtered.length, totalAmount: filtered.reduce((sum, item) => sum + item.amount, 0) };
  }
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/v1/expenses/initial") {
      return route.fulfill({ json: { cycleSettings: ledger, period: { from: "2026-10-01", to: "2026-10-31" },
        categories: [category], page: expensePage(new URL("http://test/?size=20&from=2026-10-01&to=2026-10-31")) } });
    }
    if (url.pathname === "/api/v1/expenses") {
      requests.push(url);
      return route.fulfill({ json: expensePage(url) });
    }
    if (url.pathname.startsWith("/api/v1/expenses/") && route.request().method() === "PUT") {
      const expense = expenses.find((item) => item.id === url.pathname.split("/").at(-1))!;
      const body = route.request().postDataJSON();
      Object.assign(expense, { amount: body.amount, spentOn: body.spentOn, merchant: body.merchant, memo: body.memo, version: expense.version + 1 });
      return route.fulfill({ json: expense });
    }
    if (url.pathname === "/api/v1/auth/csrf") return route.fulfill({ json: { headerName: "X-XSRF-TOKEN", token: "test-token" } });
    if (url.pathname === "/api/v1/auth/me") return route.fulfill({ json: { id: "test-user", displayName: "테스트", profileImageUrl: null } });
    return route.fulfill({ status: 204 });
  });
  return { requests };
}

const records = (page: Page) => page.locator(".expense-records > li");

for (const width of [390, 1280]) {
  test(`keeps expanded expenses and scroll after repeated edits at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockPaginatedExpenses(page);
    await page.goto("/expenses");
    await expect(records(page)).toHaveCount(20);
    await page.getByRole("button", { name: "내역 더 보기", exact: true }).click();
    await expect(records(page)).toHaveCount(40);

    for (const amount of [35000, 36000]) {
      const edit = page.getByRole("button", { name: "상호 035 수정", exact: true });
      await edit.scrollIntoViewIfNeeded();
      const scrollY = await page.evaluate(() => window.scrollY);
      await edit.click();
      const dialog = page.getByRole("dialog", { name: "식비 수정", exact: true });
      await dialog.locator("#expense-amount").fill(String(amount));
      await dialog.getByRole("button", { name: "수정 저장", exact: true }).click();
      await expect(dialog).toBeHidden();
      await expect(records(page).filter({ hasText: "상호 035" })).toContainText(`-${amount.toLocaleString("ko-KR")}원`);
      await expect(records(page)).toHaveCount(40);
      await expect.poll(() => page.evaluate(() => window.scrollY)).toBeCloseTo(scrollY, 0);
      await expect(page.getByRole("button", { name: "상호 035 수정", exact: true })).toBeInViewport();
    }
    await expect(page.getByRole("heading", { name: "65건 · 100,000원", exact: true })).toBeAttached();
    await page.getByRole("button", { name: "내역 더 보기", exact: true }).click();
    await expect(records(page)).toHaveCount(60);
    await expect(records(page).filter({ hasText: "상호 035" })).toHaveCount(1);
  });
}

test("refreshes more than 100 visible expenses and continues from a fresh cursor", async ({ page }) => {
  const { requests } = await mockPaginatedExpenses(page, 145);
  await page.goto("/expenses");
  await expect(records(page)).toHaveCount(20);
  for (const count of [40, 60, 80, 100, 120]) {
    await page.getByRole("button", { name: "내역 더 보기", exact: true }).click();
    await expect(records(page)).toHaveCount(count);
  }
  await page.getByRole("button", { name: "상호 115 수정", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "식비 수정", exact: true });
  await dialog.getByPlaceholder("예: 동네마트").fill("수정된 상호 115");
  await dialog.getByRole("button", { name: "수정 저장", exact: true }).click();
  await expect(records(page).filter({ hasText: "수정된 상호 115" })).toHaveCount(1);
  await expect(records(page)).toHaveCount(120);
  expect(requests.slice(-2).map((url) => url.searchParams.get("size"))).toEqual(["100", "20"]);
  await page.getByRole("button", { name: "내역 더 보기", exact: true }).click();
  await expect(records(page)).toHaveCount(140);
  await expect(records(page).filter({ hasText: "상호 140" })).toHaveCount(1);
});

test("resets expansion after changing cycles, filters or pages", async ({ page }) => {
  await mockPaginatedExpenses(page);
  await page.goto("/expenses");
  await page.getByRole("button", { name: "내역 더 보기", exact: true }).click();
  await expect(records(page)).toHaveCount(40);
  await page.getByRole("button", { name: "이전 주기", exact: true }).click();
  await expect(page.getByText("조건에 맞는 식비가 없습니다.", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "다음 주기", exact: true }).click();
  await expect(records(page)).toHaveCount(20);
  await page.getByRole("button", { name: "내역 더 보기", exact: true }).click();
  await expect(records(page)).toHaveCount(40);
  await page.locator(".desktop-filter-panel").getByPlaceholder("상호명 또는 메모", { exact: true }).fill("상호");
  await page.locator(".desktop-filter-panel").getByRole("button", { name: "검색", exact: true }).click();
  await expect(records(page)).toHaveCount(20);
  await page.getByRole("button", { name: "내역 더 보기", exact: true }).click();
  await expect(records(page)).toHaveCount(40);
  await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("link", { name: "통계", exact: true }).click();
  await expect(page).toHaveURL(/\/statistics$/);
  await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("link", { name: "식비", exact: true }).click();
  await expect(records(page)).toHaveCount(20);
});

test("keeps the expanded list when refreshing after a saved edit fails", async ({ page }) => {
  await mockPaginatedExpenses(page);
  await page.goto("/expenses");
  await page.getByRole("button", { name: "내역 더 보기", exact: true }).click();
  await expect(records(page)).toHaveCount(40);
  await page.getByRole("button", { name: "상호 035 수정", exact: true }).click();
  await page.route((url) => url.pathname === "/api/v1/expenses", (route) =>
    route.fulfill({ status: 503, json: { detail: "잠시 후 다시 조회해 주세요." } }), { times: 1 });
  await page.getByRole("dialog", { name: "식비 수정", exact: true }).getByRole("button", { name: "수정 저장", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "잠시 후 다시 조회해 주세요." })).toBeVisible();
  await expect(records(page)).toHaveCount(40);
  await expect(page.getByRole("button", { name: "상호 035 수정", exact: true })).toBeAttached();
});

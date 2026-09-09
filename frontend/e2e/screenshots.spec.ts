import { expect, test } from "@playwright/test";
import path from "node:path";

const screenshotDirectory = path.resolve(process.cwd(), "artifacts/screenshots");

test("capture implemented frontend screens", async ({ browser }) => {
  const desktop = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await mockExpenseApis(desktop);
  await desktop.goto("/");
  await expect(desktop.getByRole("heading", { name: "141,200원 남았어요" })).toBeVisible();
  await desktop.screenshot({
    path: path.join(screenshotDirectory, "dashboard-desktop.png"),
    fullPage: true,
    animations: "disabled",
  });
  await desktop.close();

  const mobile = await browser.newPage({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 1,
    isMobile: true,
    hasTouch: true,
  });
  await mockExpenseApis(mobile);

  const screens = [
    { route: "/", file: "dashboard-mobile.png", heading: "141,200원 남았어요" },
    { route: "/expenses", file: "expenses-mobile.png", heading: "식비 내역" },
    { route: "/statistics", file: "statistics-mobile.png", heading: "기간별 통계" },
    { route: "/settings", file: "settings-mobile.png", heading: "설정" },
    { route: "/offline", file: "offline-mobile.png", heading: "인터넷 연결이 필요해요" },
  ];

  for (const screen of screens) {
    await mobile.goto(screen.route);
    await expect(mobile.getByRole("heading", { name: screen.heading })).toBeVisible();
    if (screen.route === "/expenses") {
      await expect(mobile.getByText("동네마트", { exact: true })).toBeVisible();
    }
    await mobile.evaluate(() => window.scrollTo(0, 0));
    await mobile.screenshot({
      path: path.join(screenshotDirectory, screen.file),
      fullPage: true,
      animations: "disabled",
    });
  }

  await mobile.close();
});

async function mockExpenseApis(page: import("@playwright/test").Page) {
  const categories = [
    { id: "11111111-1111-1111-1111-111111111111", name: "장보기", sortOrder: 1, version: 0 },
    { id: "22222222-2222-2222-2222-222222222222", name: "외식", sortOrder: 2, version: 0 },
    { id: "33333333-3333-3333-3333-333333333333", name: "배달", sortOrder: 3, version: 0 },
    { id: "44444444-4444-4444-4444-444444444444", name: "카페/간식", sortOrder: 4, version: 0 },
    { id: "55555555-5555-5555-5555-555555555555", name: "기타", sortOrder: 5, version: 0 },
  ];
  const expenses = [
    { id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", amount: 18500, spentOn: "2026-09-08", category: categories[0], merchant: "동네마트", memo: "주말 장보기", version: 0, createdAt: "2026-09-08T14:00:00+09:00", updatedAt: "2026-09-08T14:00:00+09:00" },
    { id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", amount: 32000, spentOn: "2026-09-07", category: categories[1], merchant: "을지로 식당", memo: "가족 저녁", version: 0, createdAt: "2026-09-07T19:00:00+09:00", updatedAt: "2026-09-07T19:00:00+09:00" },
    { id: "cccccccc-cccc-cccc-cccc-cccccccccccc", amount: 9800, spentOn: "2026-09-06", category: categories[3], merchant: "커피하우스", memo: "커피와 간식", version: 0, createdAt: "2026-09-06T16:00:00+09:00", updatedAt: "2026-09-06T16:00:00+09:00" },
  ];

  await page.route("**/api/v1/**", async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    if (pathname === "/api/v1/categories") {
      await route.fulfill({ json: { items: categories } });
    } else if (pathname === "/api/v1/expenses") {
      await route.fulfill({ json: { items: expenses, nextCursor: null, hasNext: false } });
    } else if (pathname === "/api/v1/ledger") {
      await route.fulfill({ json: { currentUserRole: "ADMIN" } });
    } else if (pathname === "/api/v1/auth/csrf") {
      await route.fulfill({ json: { headerName: "X-XSRF-TOKEN", token: "screenshot-token" } });
    } else {
      await route.fulfill({ status: 204 });
    }
  });
}

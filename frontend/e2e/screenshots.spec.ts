import { expect, test } from "@playwright/test";
import path from "node:path";
import { budgetCycleContaining, budgetCycleStarting, cycleForDate, weeklyCycleContaining } from "../src/lib/budget-cycle";

const screenshotDirectory = path.resolve(process.cwd(), "artifacts/screenshots");

const midCycleText = "장보기 중심으로 소비했고 예산에 여유가 있습니다. 남은 기간에는 배달 빈도를 점검해 보세요.";
const pendingMidCycle = { eligible: true, status: "NOT_ANALYZED", text: null, midpoint: "2026-10-16", through: null, analyzedAt: null };
const completedMidCycle = { ...pendingMidCycle, status: "COMPLETED", text: midCycleText, through: "2026-10-20", analyzedAt: "2026-10-20T03:00:00Z" };
function dashboardWithMidCycle(analysis: typeof pendingMidCycle | typeof completedMidCycle) {
  return { yearMonth: "2026-10", cycleUnit: "MONTHLY", period: { from: "2026-10-01", to: "2026-10-31" },
    budget: 800000, spent: 20000, remaining: 780000, projectedSpent: 31000, remainingDays: 11,
    usageRate: 2.5, pushUsageThreshold: 80, status: "NORMAL", recentExpenses: [], midCycleAnalysis: analysis };
}

for (const width of [320, 390, 1024, 1440]) {
  test(`automatically creates and reuses the mid-cycle evaluation at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockExpenseApis(page);
    let generations = 0;
    let saved = false;
    let release!: () => void;
    const ready = new Promise<void>((resolve) => { release = resolve; });
    await page.route((url) => url.pathname === "/api/v1/dashboard", (route) => route.fulfill({ json: dashboardWithMidCycle(saved ? completedMidCycle : pendingMidCycle) }));
    await page.route("**/api/v1/dashboard/mid-cycle-analysis", async (route) => {
      expect(route.request().method()).toBe("POST");
      expect(route.request().postDataJSON()).toEqual({ from: "2026-10-01", to: "2026-10-31" });
      expect(route.request().headers()["x-xsrf-token"]).toBeTruthy();
      generations++;
      await ready;
      saved = true;
      await route.fulfill({ json: completedMidCycle });
    });
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "780,000원 남았어요" })).toBeVisible();
    await expect(page.getByRole("region", { name: "소비 중간평가" }).getByRole("status")).toContainText("현재까지의 소비를 분석하고 있어요.");
    release();
    const panel = page.locator(".projection-panel");
    await expect(panel.getByRole("region", { name: "소비 중간평가" })).toBeVisible();
    await expect(panel.getByLabel("저장된 중간평가")).toHaveText(midCycleText);
    await expect(panel.getByText("2026-10-20까지의 소비 기준 · 주기당 한 번 분석")).toBeVisible();
    expect(await panel.evaluate((element) => element.lastElementChild?.getAttribute("aria-label"))).toBe("소비 중간평가");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: path.join(screenshotDirectory, `mid-cycle-${width}.png`), fullPage: true });
    await page.reload();
    await expect(page.getByLabel("저장된 중간평가")).toHaveText(midCycleText);
    expect(generations).toBe(1);
  });
}

test("does not request a mid-cycle evaluation before the midpoint", async ({ page }) => {
  await mockExpenseApis(page);
  let calls = 0;
  await page.route((url) => url.pathname === "/api/v1/dashboard", (route) => route.fulfill({ json: dashboardWithMidCycle({ ...pendingMidCycle, eligible: false, status: "UNAVAILABLE" }) }));
  await page.route("**/api/v1/dashboard/mid-cycle-analysis**", async (route) => { calls++; await route.fulfill({ json: {} }); });
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "780,000원 남았어요" })).toBeVisible();
  await expect(page.getByRole("region", { name: "소비 중간평가" })).toHaveCount(0);
  expect(calls).toBe(0);
});

test("polls an existing shared evaluation without generating again", async ({ page }) => {
  await mockExpenseApis(page);
  await page.route((url) => url.pathname === "/api/v1/dashboard", (route) => route.fulfill({ json: dashboardWithMidCycle({ ...pendingMidCycle, status: "PROCESSING" }) }));
  let reads = 0;
  await page.route("**/api/v1/dashboard/mid-cycle-analysis**", async (route) => {
    expect(route.request().method()).toBe("GET");
    reads++;
    await route.fulfill({ json: completedMidCycle });
  });
  await page.goto("/");
  await expect(page.getByLabel("저장된 중간평가")).toHaveText(midCycleText);
  expect(reads).toBe(1);
});

test("keeps home available on an AI error and supports an explicit retry", async ({ page }) => {
  await mockExpenseApis(page);
  await page.route((url) => url.pathname === "/api/v1/dashboard", (route) => route.fulfill({ json: dashboardWithMidCycle(pendingMidCycle) }));
  let calls = 0;
  await page.route("**/api/v1/dashboard/mid-cycle-analysis", async (route) => {
    calls++;
    await route.fulfill(calls === 1 ? { status: 503, json: { detail: "AI 연결이 비활성화되어 있습니다." } } : { json: completedMidCycle });
  });
  await page.goto("/");
  await expect(page.getByRole("region", { name: "소비 중간평가" }).getByRole("alert")).toHaveText("AI 연결이 비활성화되어 있습니다.");
  await expect(page.getByRole("link", { name: "식비 등록", exact: true })).toBeVisible();
  expect(calls).toBe(1);
  await page.getByRole("button", { name: "다시 시도" }).click();
  await expect(page.getByLabel("저장된 중간평가")).toHaveText(midCycleText);
  expect(calls).toBe(2);
});

test("does not loop when there are no expenses to evaluate", async ({ page }) => {
  await mockExpenseApis(page);
  await page.route((url) => url.pathname === "/api/v1/dashboard", (route) => route.fulfill({ json: dashboardWithMidCycle(pendingMidCycle) }));
  let calls = 0;
  await page.route("**/api/v1/dashboard/mid-cycle-analysis", async (route) => {
    calls++;
    await route.fulfill({ json: { ...pendingMidCycle, eligible: false, status: "NO_EXPENSES", through: "2026-10-20" } });
  });
  await page.goto("/");
  await expect(page.getByText("등록된 소비 기록이 없어 중간평가를 할 수 없습니다.")).toBeVisible();
  await expect(page.getByRole("region", { name: "소비 중간평가" })).toHaveAttribute("aria-busy", "false");
  expect(calls).toBe(1);
});

test("capture implemented frontend screens", async ({ browser }) => {
  const desktop = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await mockExpenseApis(desktop);
  await desktop.goto("/");
  await expect(desktop.getByRole("heading", { name: "141,200원 남았어요" })).toBeVisible();
  await expect(desktop.getByRole("link", { name: "sikbi 홈" })).toBeVisible();
  await expect(desktop.getByRole("link", { name: "sikbi 홈" }).locator("img")).toHaveAttribute("src", "/icon.svg");
  await expect(desktop.getByText("sikbi - 함께 쓰는 식비 관리")).toBeVisible();
  await desktop.screenshot({
    path: path.join(screenshotDirectory, "dashboard-desktop.png"),
    fullPage: true,
    animations: "disabled",
  });
  await desktop.close();

  const authMobile = await browser.newPage({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 1,
    isMobile: true,
    hasTouch: true,
  });
  await mockExpenseApis(authMobile, false);
  const authScreens = [
    { route: "/login", file: "login-mobile.png", heading: "로그인" },
    { route: "/signup", file: "signup-mobile.png", heading: "새 장부 만들기" },
    { route: "/join?code=MBD-TEST-7K2P", file: "join-mobile.png", heading: "초대받은 장부에 참여" },
    { route: "/setup", file: "setup-mobile.png", heading: "서비스 관리자 설정" },
    { route: "/forgot-password", file: "forgot-password-mobile.png", heading: "비밀번호 찾기" },
    { route: "/reset-password?token=test-reset-token", file: "reset-password-mobile.png", heading: "새 비밀번호 설정" },
  ];
  for (const screen of authScreens) {
    await authMobile.goto(screen.route);
    await expect(authMobile.getByRole("heading", { name: screen.heading })).toBeVisible();
    if (screen.route === "/login") {
      await expect(authMobile.getByRole("textbox", { name: "이메일" })).not.toHaveAttribute("placeholder");
    }
    await authMobile.screenshot({
      path: path.join(screenshotDirectory, screen.file),
      fullPage: true,
      animations: "disabled",
    });
  }
  await authMobile.close();

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
    { route: "/settings/budget", file: "budget-settings-mobile.png", heading: "예산 관리" },
    { route: "/settings/notifications", file: "notification-settings-mobile.png", heading: "푸시 알림" },
    { route: "/settings/categories", file: "category-settings-mobile.png", heading: "카테고리 관리" },
    { route: "/settings/members", file: "member-settings-mobile.png", heading: "멤버 관리" },
    { route: "/settings/invitations", file: "invitation-settings-mobile.png", heading: "초대 코드" },
    { route: "/settings/account", file: "account-settings-mobile.png", heading: "계정 설정" },
    { route: "/offline", file: "offline-mobile.png", heading: "인터넷 연결이 필요해요" },
  ];

  for (const screen of screens) {
    await mobile.goto(screen.route);
    await expect(mobile.getByRole("heading", { level: 1, name: screen.heading })).toBeVisible();
    if (screen.route === "/") {
      await expect(mobile.getByText("현재 사용률", { exact: true })).toBeVisible();
      await expect(mobile.getByText("82.4%", { exact: true })).toBeVisible();
      await expect(mobile.getByText("종료까지 D-18", { exact: true })).toBeVisible();
    } else if (screen.route === "/expenses") {
      await expect(mobile.getByText("동네마트", { exact: true })).toBeVisible();
    } else if (screen.route === "/statistics") {
      await expect(mobile.getByText("658,800원", { exact: true })).toBeVisible();
    } else if (screen.route === "/settings") {
      await expect(mobile.getByRole("link", { name: /예산 관리/ })).toBeVisible();
      await expect(mobile.getByRole("link", { name: /멤버 관리/ })).toBeVisible();
      await expect(mobile.getByRole("link", { name: /푸시 알림/ })).toBeVisible();
      await expect(mobile.getByRole("region", { name: "계정", exact: true }).getByRole("link", { name: /계정 설정/ })).toBeVisible();
    } else if (screen.route === "/settings/budget") {
      await expect(mobile.getByText("현재 적용 금액", { exact: true })).toBeVisible();
      await expect(mobile.getByRole("heading", { name: "예산 주기" })).toBeVisible();
    } else if (screen.route === "/settings/notifications") {
      await expect(mobile.getByRole("heading", { name: "Push 기준 사용률" })).toBeVisible();
      await expect(mobile.getByRole("button", { name: "이 기기 알림 활성화" })).toBeVisible();
    } else if (screen.route === "/settings/categories") {
      await expect(mobile.getByRole("heading", { name: "사용 중인 카테고리" })).toBeVisible();
      await expect(mobile.getByRole("button", { name: "새 카테고리 추가" })).toBeVisible();
      await expect(mobile.getByText("장보기", { exact: true })).toBeVisible();
    } else if (screen.route === "/settings/members") {
      await expect(mobile.getByRole("heading", { name: "우리집 식비 멤버" })).toBeVisible();
      await expect(mobile.getByText("힘찬", { exact: true })).toBeVisible();
      await expect(mobile.getByText("가족", { exact: true })).toBeVisible();
    } else if (screen.route === "/settings/invitations") {
      await expect(mobile.getByRole("heading", { name: "발급한 초대 코드" })).toBeVisible();
      await expect(mobile.getByRole("button", { name: "새 초대 코드 발급" })).toBeVisible();
      await expect(mobile.getByText("MBD-****7K2P", { exact: true })).toBeVisible();
    } else if (screen.route === "/settings/account") {
      await expect(mobile.getByRole("heading", { name: "프로필 사진" })).toBeVisible();
      await expect(mobile.getByText("DANGER ZONE")).toHaveCount(0);
      await expect(mobile.getByText(/업로드 전에 원하는 영역/)).toHaveCount(0);
      await expect(mobile.getByRole("heading", { name: "비밀번호 변경" })).toBeVisible();
      await expect(mobile.getByRole("heading", { name: "공유 장부 종료" })).toBeVisible();
      await expect(mobile.getByText("himchan@example.com", { exact: true })).toBeVisible();
    }
    await mobile.evaluate(() => window.scrollTo(0, 0));
    await mobile.screenshot({
      path: path.join(screenshotDirectory, screen.file),
      fullPage: true,
      animations: "disabled",
    });
    if (screen.route === "/expenses") {
      await mobile.getByRole("button", { name: "식비 등록" }).click();
      await mobile.getByLabel("이미지 추가").setInputFiles({
        name: "receipt.png",
        mimeType: "image/png",
        buffer: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=", "base64"),
      });
      await mobile.getByRole("button", { name: "크롭 적용" }).click();
      await expect(mobile.getByRole("button", { name: "이미지 제거" })).toBeVisible();
      await mobile.screenshot({
        path: path.join(screenshotDirectory, "expense-image-upload-mobile.png"),
        fullPage: true,
        animations: "disabled",
      });
      await mobile.getByRole("button", { name: "닫기" }).click();
    }
    if (screen.route === "/settings/invitations") {
      await mobile.getByRole("button", { name: "새 초대 코드 발급" }).click();
      await expect(mobile.getByRole("heading", { name: "새 초대 코드가 발급됐어요" })).toBeVisible();
      await mobile.screenshot({
        path: path.join(screenshotDirectory, "invitation-issued-mobile.png"),
        fullPage: true,
        animations: "disabled",
      });
    }
    if (screen.route === "/settings/account") {
      await mobile.getByRole("button", { name: "장부 종료" }).click();
      await expect(mobile.getByRole("dialog", { name: "우리집 식비 장부를 종료할까요?" })).toBeVisible();
      await mobile.screenshot({
        path: path.join(screenshotDirectory, "account-ledger-deletion-mobile.png"),
        fullPage: true,
        animations: "disabled",
      });
      await mobile.getByRole("dialog", { name: "우리집 식비 장부를 종료할까요?" }).getByRole("button", { name: "취소" }).click();
    }
  }

  await mobile.close();
});

test("shows a clean settings list without availability labels", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/settings");

  await expect(page.getByText("사용 가능", { exact: true })).toHaveCount(0);
  await expect(page.getByText("MANAGEMENT", { exact: true })).toHaveCount(0);
  await expect(page.getByText("6개 메뉴", { exact: true })).toHaveCount(0);
  await expect(page.getByText("설정 메뉴", { exact: true })).toHaveCount(0);
  await expect(page.getByText("설정", { exact: true }).first()).toBeVisible();
  await expect(page.getByRole("link", { name: /예산 관리/ })).toBeVisible();
});

test("uses the same image thumbnail and date tile as the expense list on home", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/");
  const recent = page.locator(".expense-list");
  await expect(recent.locator("li").first().locator(".expense-thumbnail"))
    .toHaveAttribute("src", /\/api\/v1\/images\/.*\/thumbnail/);
  await expect(recent.locator("li").nth(1).locator(".expense-date-box"))
    .toContainText("07");
});

test("switches the shared budget from a monthly start date to a weekly start weekday", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/settings/budget");
  await expect(page.getByLabel("예산 주기 시작일")).toBeVisible();
  await page.getByRole("button", { name: "예산 주기 단위" }).click();
  await page.getByRole("option", { name: "주", exact: true }).click();
  await expect(page.getByLabel("예산 주기 시작일")).toHaveCount(0);
  await page.getByRole("button", { name: "예산 주기 시작 요일" }).click();
  await page.getByRole("option", { name: "목요일" }).click();
  await page.getByLabel("기본 주 예산").fill("200000");
  await page.getByRole("button", { name: "예산 주기 저장" }).click();
  await expect(page.getByText("예산 주기를 변경했습니다.")).toBeVisible();
  await expect(page.getByText("기본 주 예산", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("200,000원")).toBeVisible();

  const requestForWeeklyRange = page.waitForResponse((response) => response.url().endsWith("/api/v1/statistics/initial"));
  await page.goto("/statistics");
  const initial = await (await requestForWeeklyRange).json();
  expect(initial.cycleSettings.budgetCycleUnit).toBe("WEEKLY");
  expect(initial.cycleSettings.budgetWeekStartDay).toBe(4);
  expect(initial.statistics.period.from).toBe(weeklyCycleContaining(
    new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date()), 4,
  ).from);
});

for (const view of ["expenses", "statistics"]) {
  test(`loads ${view} in one initial data request`, async ({ page }) => {
    await mockExpenseApis(page);
    const requests: string[] = [];
    page.on("request", (request) => {
      const pathname = new URL(request.url()).pathname;
      if (["/api/v1/ledger", "/api/v1/categories", "/api/v1/expenses", "/api/v1/expenses/initial", "/api/v1/statistics", "/api/v1/statistics/initial"].includes(pathname)) requests.push(pathname);
    });
    await page.goto(`/${view}`);
    if (view === "expenses") await expect(page.getByRole("heading", { name: "3건 · 60,300원" })).toBeVisible();
    else await expect(page.getByRole("region", { name: "통계 요약" })).toBeVisible();
    expect(requests).toEqual([`/api/v1/${view}/initial`]);
  });
}

test("does not add a whole-chart focus target", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/statistics");

  const chart = page.getByRole("img", { name: "일별 지출 추이 차트" });
  await expect(chart).toBeVisible();
  await expect(chart.locator('[tabindex="0"]')).toHaveCount(0);
  const internalFocusTarget = chart.locator("[tabindex]").first();
  if (await internalFocusTarget.count()) {
    await internalFocusTarget.focus();
    await expect(internalFocusTarget).toHaveCSS("outline-style", "none");
  }
});

test("shows same-point comparison dates and amounts separately from full-period spending", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.clock.setFixedTime(new Date("2026-10-09T12:00:00+09:00"));
  await mockExpenseApis(page);
  await page.route(/\/api\/v1\/statistics(?:\/initial)?(?:\?.*)?$/, (route) => {
    const previous = new URL(route.request().url()).searchParams.get("from") === "2026-09-01";
    const statistics = {
      period: { from: previous ? "2026-09-01" : "2026-10-01", to: previous ? "2026-09-30" : "2026-10-31" },
      totalAmount: 211000, budget: { amount: 500000, usageRate: 42.2 },
      comparison: {
        from: previous ? "2026-08-01" : "2026-09-01", to: previous ? "2026-08-31" : "2026-09-09",
        totalAmount: 100000, currentAmount: previous ? 88000 : 112000,
        currentPeriod: { from: previous ? "2026-09-01" : "2026-10-01", to: previous ? "2026-09-30" : "2026-10-09" },
        changeAmount: previous ? -12000 : 12000, changeRate: previous ? -12 : 12, samePoint: !previous, available: true,
      }, daily: [], categories: [],
    };
    return route.fulfill({ json: new URL(route.request().url()).pathname.endsWith("/initial")
      ? { cycleSettings: { budgetCycleUnit: "MONTHLY", budgetCycleStartDay: 1, budgetWeekStartDay: 1 }, statistics } : statistics });
  });
  await page.goto("/statistics");
  const summary = page.getByRole("region", { name: "통계 요약" });
  const comparison = summary.locator("article").nth(2);
  await expect(summary.locator("article").first()).toContainText("211,000원");
  await expect(comparison).toContainText("이전 주기 같은 시점 대비");
  await expect(comparison).toContainText("2026년 9월 1일~9일보다 12,000원 더 썼어요 (+12.0%)");
  await expect(comparison).toContainText("비교한 지출: 2026년 10월 1일~9일 · 112,000원");
  await expect(comparison.getByText("12,000원", { exact: true })).toBeVisible();
  await page.screenshot({ path: path.join(screenshotDirectory, "statistics-same-point-mobile.png"), fullPage: true });
  await page.getByRole("button", { name: "직전 주기", exact: true }).click();
  await expect(comparison).toContainText("이전 주기 대비");
  await expect(comparison).not.toContainText("같은 시점");
  await expect(comparison).toContainText("2026년 8월 1일~31일보다 12,000원 덜 썼어요 (-12.0%)");
});

test("shows an honest comparison message when the previous amount is zero or the period has not started", async ({ page }) => {
  await mockExpenseApis(page);
  let available = true;
  await page.route(/\/api\/v1\/statistics(?:\/initial)?(?:\?.*)?$/, (route) => {
    const statistics = {
      period: { from: "2026-10-01", to: "2026-10-31" }, totalAmount: 12000,
      budget: { amount: 500000, usageRate: 2.4 },
      comparison: { from: "2026-09-01", to: "2026-09-09", totalAmount: 0, changeAmount: 12000, changeRate: null,
        currentPeriod: null, currentAmount: 12000, samePoint: true, available },
      daily: [], categories: [],
    };
    return route.fulfill({ json: new URL(route.request().url()).pathname.endsWith("/initial")
      ? { cycleSettings: { budgetCycleUnit: "MONTHLY", budgetCycleStartDay: 1, budgetWeekStartDay: 1 }, statistics } : statistics });
  });
  await page.goto("/statistics");
  const comparison = page.getByRole("region", { name: "통계 요약" }).locator("article").nth(2);
  await expect(comparison).toContainText("이전 지출이 없어 증감률을 계산할 수 없어요");
  await expect(comparison).not.toContainText("Infinity");
  available = false;
  await page.getByRole("button", { name: "직전 주기", exact: true }).click();
  await expect(comparison).toContainText("아직 비교할 수 없어요");
  await expect(comparison).not.toContainText("12,000원");
});

const navigationCases = [
  { name: "monthly partial", unit: "MONTHLY", start: 1, week: 1, today: "2026-10-12", custom: ["2026-09-10", "2026-09-20"], previous: ["2026-08-01", "2026-08-31"], next: ["2026-10-01", "2026-10-31"] },
  { name: "monthly spanning cycles", unit: "MONTHLY", start: 1, week: 1, today: "2026-10-12", custom: ["2026-08-15", "2026-09-10"], previous: ["2026-07-01", "2026-07-31"], next: ["2026-10-01", "2026-10-31"] },
  { name: "monthly custom start", unit: "MONTHLY", start: 25, week: 1, today: "2026-10-12", custom: ["2026-08-28", "2026-09-10"], previous: ["2026-07-25", "2026-08-24"], next: ["2026-09-25", "2026-10-24"] },
  { name: "monthly leap year", unit: "MONTHLY", start: 31, week: 1, today: "2028-03-31", custom: ["2028-02-29", "2028-03-05"], previous: ["2028-01-31", "2028-02-28"], next: ["2028-03-31", "2028-04-29"] },
  { name: "weekly partial", unit: "WEEKLY", start: 1, week: 4, today: "2026-10-12", custom: ["2026-10-02", "2026-10-04"], previous: ["2026-09-24", "2026-09-30"], next: ["2026-10-08", "2026-10-14"] },
  { name: "weekly spanning cycles", unit: "WEEKLY", start: 1, week: 4, today: "2026-10-12", custom: ["2026-09-30", "2026-10-06"], previous: ["2026-09-17", "2026-09-23"], next: ["2026-10-08", "2026-10-14"] },
] as const;

for (const scenario of navigationCases) {
  for (const direction of ["previous", "next"] as const) {
    test(`navigates outside the queried period (${scenario.name}, ${direction})`, async ({ page }) => {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.clock.setFixedTime(new Date(`${scenario.today}T12:00:00+09:00`));
      await mockExpenseApis(page);
      const initialCycle = cycleForDate(scenario.today, scenario.unit, scenario.start, scenario.week);
      const queries: string[] = [];
      await page.route(/\/api\/v1\/statistics(?:\/initial)?(?:\?.*)?$/, (route) => {
        const url = new URL(route.request().url());
        const initial = url.pathname.endsWith("/initial");
        const from = initial ? initialCycle.from : url.searchParams.get("from")!;
        const to = initial ? initialCycle.to : url.searchParams.get("to")!;
        if (!initial) queries.push(`${from}/${to}`);
        const cycle = cycleForDate(from, scenario.unit, scenario.start, scenario.week);
        const eligible = cycle.from === from && cycle.to === to && to < scenario.today;
        const statistics = { period: { from, to }, totalAmount: 120000,
          budget: { amount: 500000, usageRate: 24 },
          comparison: { from: "2026-08-01", to: "2026-08-31", totalAmount: 100000, changeAmount: 20000, changeRate: 20 },
          daily: [], categories: [], spendingAnalysis: { eligible, status: eligible ? "NOT_ANALYZED" : "UNAVAILABLE", text: null, analyzedAt: null } };
        return route.fulfill({ json: initial
          ? { cycleSettings: { budgetCycleUnit: scenario.unit, budgetCycleStartDay: scenario.start, budgetWeekStartDay: scenario.week }, statistics } : statistics });
      });
      await page.goto("/statistics");
      const summary = page.getByRole("region", { name: "통계 요약", exact: true });
      await expect(summary).toContainText(`${initialCycle.from} ~ ${initialCycle.to}`);
      async function chooseDate(label: string, current: string, target: string) {
        await page.getByRole("button", { name: label, exact: true }).click();
        const [year, month, day] = target.split("-").map(Number);
        const [currentYear, currentMonth] = current.split("-").map(Number);
        const delta = (year - currentYear) * 12 + month - currentMonth;
        const dialog = page.getByRole("dialog", { name: label, exact: true });
        for (let count = 0; count < Math.abs(delta); count++) {
          await dialog.getByRole("button", { name: delta < 0 ? "이전 달" : "다음 달", exact: true }).click();
        }
        await dialog.getByRole("button", { name: `${year}년 ${month}월 ${day}일`, exact: true }).click();
        await expect(dialog).toHaveCount(0);
      }
      await chooseDate("통계 시작일", initialCycle.from, scenario.custom[0]);
      await chooseDate("통계 종료일", initialCycle.to, scenario.custom[1]);
      await page.getByRole("button", { name: "조회", exact: true }).click();
      await expect(summary).toContainText(`${scenario.custom[0]} ~ ${scenario.custom[1]}`);
      // An unsubmitted date edit must not move the navigation's successful-query anchor.
      if (scenario.name === "monthly partial") {
        await chooseDate("통계 종료일", scenario.custom[1], "2026-09-21");
      }
      await page.getByRole("button", { name: direction === "previous" ? "이전 주기" : "다음 주기", exact: true }).click();
      const target = scenario[direction];
      await expect(summary).toContainText(`${target[0]} ~ ${target[1]}`);
      expect(queries).toEqual([scenario.custom.join("/"), target.join("/")]);
      const [startYear, startMonth, startDay] = target[0].split("-").map(Number);
      const [endYear, endMonth, endDay] = target[1].split("-").map(Number);
      await expect(page.getByRole("button", { name: "통계 시작일", exact: true })).toContainText(`${startYear}년 ${startMonth}월 ${startDay}일`);
      await expect(page.getByRole("button", { name: "통계 종료일", exact: true })).toContainText(`${endYear}년 ${endMonth}월 ${endDay}일`);
      if (scenario.name === "monthly partial" && direction === "previous") {
        await page.getByRole("button", { name: "이전 주기", exact: true }).click();
        await expect(summary).toContainText("2026-07-01 ~ 2026-07-31");
        await page.getByRole("button", { name: "다음 주기", exact: true }).click();
        await expect(summary).toContainText("2026-08-01 ~ 2026-08-31");
        await page.screenshot({ path: path.join(screenshotDirectory, "statistics-cycle-navigation-mobile.png"), fullPage: true });
      }
    });
  }
}

for (const unit of ["MONTHLY", "WEEKLY"] as const) {
  test(`hides spending analysis as soon as selected dates stop matching the loaded cycle (${unit})`, async ({ page }) => {
    await page.clock.setFixedTime(new Date("2026-10-12T12:00:00+09:00"));
    await mockExpenseApis(page);
    const weekly = unit === "WEEKLY";
    const complete = weekly ? { from: "2026-10-05", to: "2026-10-11" } : { from: "2026-09-01", to: "2026-09-30" };
    const partialTo = weekly ? "2026-10-08" : "2026-09-15";
    const crossedFrom = weekly ? "2026-10-02" : "2026-08-15";
    let pendingResponse: Promise<void> | undefined;
    await page.route(/\/api\/v1\/statistics(?:\/initial)?(?:\?.*)?$/, async (route) => {
      const url = new URL(route.request().url());
      const initial = url.pathname.endsWith("/initial");
      const from = initial ? weekly ? "2026-10-12" : "2026-10-01" : url.searchParams.get("from")!;
      const to = initial ? weekly ? "2026-10-18" : "2026-10-31" : url.searchParams.get("to")!;
      const eligible = from === complete.from && to === complete.to;
      const statistics = { period: { from, to }, totalAmount: 120000,
        budget: { amount: 500000, usageRate: 24 },
        comparison: { from: "2026-08-01", to: "2026-08-31", totalAmount: 100000, changeAmount: 20000, changeRate: 20 },
        daily: [], categories: [], spendingAnalysis: { eligible, status: eligible ? "NOT_ANALYZED" : "UNAVAILABLE", text: null, analyzedAt: null } };
      if (!initial) await pendingResponse;
      return route.fulfill({ json: initial
        ? { cycleSettings: { budgetCycleUnit: unit, budgetCycleStartDay: 1, budgetWeekStartDay: 1 }, statistics } : statistics });
    });
    let calls = 0;
    await page.route("**/api/v1/statistics/analysis", () => { calls++; });
    await page.goto("/statistics");
    await page.getByRole("button", { name: "직전 주기", exact: true }).click();
    const panel = page.getByRole("region", { name: "소비 분석", exact: true });
    await expect(panel.getByRole("button", { name: "소비 분석", exact: true })).toBeVisible();
    await page.getByRole("button", { name: "통계 종료일", exact: true }).click();
    await page.getByRole("button", { name: weekly ? "2026년 10월 8일" : "2026년 9월 15일", exact: true }).click();
    await expect(panel).toHaveCount(0);
    await page.getByRole("button", { name: "조회", exact: true }).click();
    await expect(page.getByRole("region", { name: "통계 요약" })).toContainText(`${complete.from} ~ ${partialTo}`);
    await expect(panel).toHaveCount(0);
    await page.getByRole("button", { name: "통계 시작일", exact: true }).click();
    if (!weekly) await page.getByRole("dialog", { name: "통계 시작일", exact: true }).getByRole("button", { name: "이전 달", exact: true }).click();
    await page.getByRole("button", { name: weekly ? "2026년 10월 2일" : "2026년 8월 15일", exact: true }).click();
    await page.getByRole("button", { name: "조회", exact: true }).click();
    await expect(page.getByRole("region", { name: "통계 요약" })).toContainText(`${crossedFrom} ~ ${partialTo}`);
    await expect(panel).toHaveCount(0);
    await page.getByRole("button", { name: "직전 주기", exact: true }).click();
    await expect(panel.getByRole("button", { name: "소비 분석", exact: true })).toBeVisible();
    let releaseResponse!: () => void;
    pendingResponse = new Promise<void>((resolve) => { releaseResponse = resolve; });
    await page.getByRole("button", { name: "조회", exact: true }).click();
    try {
      await expect(panel).toHaveCount(0);
    } finally {
      releaseResponse();
    }
    await expect(panel.getByRole("button", { name: "소비 분석", exact: true })).toBeVisible();
    pendingResponse = undefined;
    await page.getByRole("button", { name: "최근 3개 주기", exact: true }).click();
    await expect(panel).toHaveCount(0);
    await expect(page.getByRole("button", { name: "조회", exact: true })).toBeEnabled();
    expect(calls).toBe(0);
  });
}

for (const unit of ["MONTHLY", "WEEKLY"] as const) {
  test(`shows a fixed empty-cycle notice without an AI request (${unit})`, async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.clock.setFixedTime(new Date("2026-10-12T12:00:00+09:00"));
    await mockExpenseApis(page);
    let hasExpenses = false;
    let calls = 0;
    const previous = unit === "WEEKLY" ? { from: "2026-10-05", to: "2026-10-11" } : { from: "2026-09-01", to: "2026-09-30" };
    await page.route(/\/api\/v1\/statistics(?:\/initial)?(?:\?.*)?$/, (route) => {
      const url = new URL(route.request().url());
      const initial = url.pathname.endsWith("/initial");
      const from = initial ? unit === "WEEKLY" ? "2026-10-12" : "2026-10-01" : url.searchParams.get("from")!;
      const to = initial ? unit === "WEEKLY" ? "2026-10-18" : "2026-10-31" : url.searchParams.get("to")!;
      const completed = from === previous.from && to === previous.to;
      const statistics = { period: { from, to }, totalAmount: hasExpenses ? 12000 : 0,
        budget: { amount: 500000, usageRate: 0 },
        comparison: { from: "2026-08-01", to: "2026-08-31", totalAmount: 0, changeAmount: 0, changeRate: null },
        daily: hasExpenses ? [{ date: previous.from, amount: 12000 }] : [], categories: [],
        spendingAnalysis: { eligible: completed && hasExpenses, status: !completed ? "UNAVAILABLE" : hasExpenses ? "NOT_ANALYZED" : "NO_EXPENSES", text: null, analyzedAt: null } };
      return route.fulfill({ json: initial
        ? { cycleSettings: { budgetCycleUnit: unit, budgetCycleStartDay: 1, budgetWeekStartDay: 1 }, statistics } : statistics });
    });
    await page.route("**/api/v1/statistics/analysis", (route) => { calls++; return route.abort(); });
    await page.goto("/statistics");
    await page.getByRole("button", { name: "이전 주기", exact: true }).click();
    const panel = page.getByRole("region", { name: "소비 분석", exact: true });
    await expect(panel).toContainText("이 주기에는 등록된 소비 기록이 없어 분석할 수 없습니다.");
    await expect(panel.getByRole("button")).toHaveCount(0);
    await expect(panel).not.toContainText("OpenAI");
    await page.reload();
    await page.getByRole("button", { name: "이전 주기", exact: true }).click();
    await expect(panel).toContainText("이 주기에는 등록된 소비 기록이 없어 분석할 수 없습니다.");
    expect(calls).toBe(0);
    hasExpenses = true;
    await page.getByRole("button", { name: "조회", exact: true }).click();
    await expect(panel.getByRole("button", { name: "소비 분석", exact: true })).toBeVisible();
    await expect(panel).not.toContainText("등록된 소비 기록이 없어");
    expect(calls).toBe(0);
    await page.getByRole("button", { name: "현재 주기", exact: true }).click();
    await expect(panel).toHaveCount(0);
  });
}

test("saves a completed-cycle spending analysis and reloads it without another AI request", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.clock.setFixedTime(new Date("2026-10-12T12:00:00+09:00"));
  await mockExpenseApis(page);
  let saved: { eligible: boolean; status: string; text: string | null; analyzedAt: string | null } | null = null;
  let attempts = 0;
  const summary = "이번 주기는 장보기 중심으로 소비했으며 예산 사용률은 낮지만 이전 주기보다 총지출이 늘었습니다. 지출이 가장 많았던 날과 상위 카테고리의 비중을 함께 살펴보면 다음 주기에서 점검할 항목을 정하는 데 도움이 됩니다. 다만 집계 정보만으로는 지출이 늘어난 원인이나 개별 구매의 필요성을 판단하기 어렵습니다. 다음 주기에는 필요한 품목과 구매 순서를 미리 정하고 카테고리별 지출을 주기적으로 확인해 보세요. 예산을 유지하면서 자주 지출하는 항목을 먼저 점검하되, 식비 기록이 빠진 날짜가 없는지도 확인하면 주기별 비교를 더 정확하게 할 수 있습니다.";
  await page.route(/\/api\/v1\/statistics(?:\/initial)?(?:\?.*)?$/, (route) => {
    const url = new URL(route.request().url());
    const previous = url.searchParams.get("from") === "2026-09-01";
    const statistics = { period: { from: previous ? "2026-09-01" : "2026-10-01", to: previous ? "2026-09-30" : "2026-10-31" },
      totalAmount: 120000, budget: { amount: 500000, usageRate: 24 },
      comparison: { from: "2026-08-01", to: "2026-08-31", totalAmount: 100000, changeAmount: 20000, changeRate: 20 },
      daily: [], categories: [], spendingAnalysis: previous ? saved ?? { eligible: true, status: "NOT_ANALYZED", text: null, analyzedAt: null }
        : { eligible: false, status: "UNAVAILABLE", text: null, analyzedAt: null } };
    return route.fulfill({ json: url.pathname.endsWith("/initial")
      ? { cycleSettings: { budgetCycleUnit: "MONTHLY", budgetCycleStartDay: 1, budgetWeekStartDay: 1 }, statistics } : statistics });
  });
  await page.route("**/api/v1/statistics/analysis", async (route) => {
    attempts++;
    expect(route.request().headers()["x-xsrf-token"]).toBe("screenshot-token");
    expect(route.request().postDataJSON()).toEqual({ from: "2026-09-01", to: "2026-09-30" });
    if (attempts === 1) {
      await route.fulfill({ status: 503, json: { detail: "OpenAI 사용 한도 또는 잔액을 확인해 주세요." } });
      return;
    }
    saved = { eligible: true, status: "COMPLETED", text: summary, analyzedAt: "2026-10-12T03:00:00Z" };
    await route.fulfill({ json: { period: { from: "2026-09-01", to: "2026-09-30" }, analysis: saved } });
  });
  await page.goto("/statistics");
  await expect(page.getByRole("region", { name: "통계 요약" })).toBeVisible();
  await expect(page.getByRole("button", { name: "소비 분석", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "직전 주기", exact: true }).click();
  const panel = page.getByRole("region", { name: "소비 분석", exact: true });
  await expect(panel).toBeVisible();
  await expect(panel.getByText("이 주기의 소비를 분석하여 정리합니다.", { exact: true })).toBeVisible();
  expect(attempts).toBe(0);
  await panel.getByRole("button", { name: "소비 분석", exact: true }).click();
  await expect(panel.getByRole("alert")).toContainText("OpenAI 사용 한도 또는 잔액을 확인해 주세요.");
  await panel.getByRole("button", { name: "소비 분석", exact: true }).click();
  await expect(panel.getByText(summary, { exact: true })).toBeVisible();
  expect(await panel.evaluate((element) => element.getBoundingClientRect().bottom)).toBeLessThanOrEqual(
    await page.getByRole("region", { name: "통계 요약", exact: true }).evaluate((element) => element.getBoundingClientRect().top));
  await expect(panel.getByRole("button", { name: "소비 분석", exact: true })).toHaveCount(0);
  expect(attempts).toBe(2);
  await page.screenshot({ path: path.join(screenshotDirectory, "spending-analysis-mobile.png"), fullPage: true });
  await page.reload();
  await page.getByRole("button", { name: "직전 주기", exact: true }).click();
  await expect(panel.getByText(summary, { exact: true })).toBeVisible();
  expect(attempts).toBe(2);
  await page.getByRole("button", { name: "현재 주기", exact: true }).click();
  await expect(panel).toHaveCount(0);
});

test("tests OpenAI only after an administrator clicks the connection button", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockExpenseApis(page);
  await page.route("**/api/v1/admin/ai/status", (route) => route.fulfill({ json: { enabled: true, configured: true, ready: true, model: "gpt-5.4-mini" } }));
  let attempts = 0;
  await page.route("**/api/v1/admin/ai/connection-test", async (route) => {
    attempts++;
    expect(route.request().method()).toBe("POST");
    expect(route.request().headers()["x-xsrf-token"]).toBe("screenshot-token");
    if (attempts === 1) await route.fulfill({ status: 503, json: { detail: "OpenAI 사용 한도 또는 잔액을 확인해 주세요." } });
    else await route.fulfill({ json: { model: "gpt-5.4-mini", text: "Sikbi AI 연결 성공", inputTokens: 25, outputTokens: 8 } });
  });
  await page.goto("/settings/account");
  await page.getByRole("link", { name: "AI 연결 확인" }).click();
  const button = page.getByRole("button", { name: "연결 테스트", exact: true });
  await expect(button).toBeEnabled();
  expect(attempts).toBe(0);
  await expect(page.getByText("등록됨", { exact: true })).toBeVisible();
  await button.click();
  await expect(page.getByRole("region", { name: "OpenAI 연결 상태" }).getByRole("alert")).toContainText("OpenAI 사용 한도 또는 잔액을 확인해 주세요.");
  await expect(page.getByText("OpenAI 호출 성공", { exact: true })).toHaveCount(0);
  await button.click();
  const result = page.getByRole("status", { name: "연결 테스트 결과" });
  await expect(result).toContainText("Sikbi AI 연결 성공");
  await expect(result).toContainText("gpt-5.4-mini");
  expect(attempts).toBe(2);
  await page.screenshot({ path: path.join(screenshotDirectory, "ai-connection-mobile.png"), fullPage: true });
});

for (const scenario of ["not configured", "forbidden"]) {
  test(`does not allow an OpenAI test when ${scenario}`, async ({ page }) => {
    await mockExpenseApis(page);
    await page.route("**/api/v1/admin/ai/status", (route) => scenario === "forbidden"
      ? route.fulfill({ status: 403, json: { detail: "서비스 관리자 권한이 필요합니다." } })
      : route.fulfill({ json: { enabled: true, configured: false, ready: false, model: "gpt-5.4-mini" } }));
    await page.goto("/settings/ai");
    if (scenario === "forbidden") {
      await expect(page.getByRole("region", { name: "OpenAI 연결 상태" }).getByRole("alert")).toContainText("서비스 관리자 권한이 필요합니다.");
      await expect(page.getByRole("button", { name: "연결 테스트", exact: true })).toHaveCount(0);
    } else {
      await expect(page.getByText("미등록", { exact: true })).toBeVisible();
      await expect(page.getByRole("button", { name: "연결 테스트", exact: true })).toBeDisabled();
    }
  });
}

test("manage categories from settings", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/settings/categories");

  await page.getByRole("button", { name: "새 카테고리 추가" }).click();
  await page.getByLabel("카테고리 이름").fill("회사 점심");
  await page.getByLabel("정렬 순서").fill("6");
  await page.getByRole("button", { name: "카테고리 추가", exact: true }).click();
  await expect(page.getByText("회사 점심", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "회사 점심 수정" }).click();
  await page.getByLabel("카테고리 이름").fill("점심");
  await page.getByRole("button", { name: "변경사항 저장" }).click();
  await expect(page.getByText("점심", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "점심 삭제" }).click();
  const dialog = page.getByRole("dialog", { name: "점심 카테고리를 삭제할까요?" });
  await expect(dialog.getByText(/기존 식비 내역은 삭제되지 않고/)).toBeVisible();
  await dialog.getByRole("button", { name: "삭제", exact: true }).click();
  await expect(page.getByText("점심", { exact: true })).toHaveCount(0);
});

test("manage administrator role from settings", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/settings/members");

  const familyRow = page.getByRole("listitem").filter({ hasText: "가족" });
  await familyRow.getByRole("button", { name: "관리자로 지정" }).click();
  let dialog = page.getByRole("dialog", { name: "가족 님을 관리자로 지정할까요?" });
  await expect(dialog.getByText(/예산과 카테고리를 변경하고/)).toBeVisible();
  await dialog.getByRole("button", { name: "권한 변경" }).click();
  await expect(familyRow.getByText("관리자", { exact: true })).toBeVisible();

  await familyRow.getByRole("button", { name: "일반 멤버로 변경" }).click();
  dialog = page.getByRole("dialog", { name: "가족 님을 일반 멤버로 변경할까요?" });
  await dialog.getByRole("button", { name: "권한 변경" }).click();
  await expect(familyRow.getByText("일반 멤버", { exact: true })).toBeVisible();

  const currentUserRow = page.getByRole("listitem").filter({ hasText: "힘찬" });
  await expect(currentUserRow.getByRole("button", { name: "일반 멤버로 변경" })).toBeDisabled();
});

test("issue, copy, and revoke an invitation code", async ({ page, context }) => {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await mockExpenseApis(page);
  await page.goto("/settings/invitations");

  await page.getByRole("button", { name: "새 초대 코드 발급" }).click();
  await expect(page.getByRole("heading", { name: "새 초대 코드가 발급됐어요" })).toBeVisible();
  await expect(page.getByText("MBD-ABCD1234-EFGH5678-IJKL9012", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "초대 코드 복사" }).click();
  await expect(page.getByText("초대 코드를 복사했습니다.", { exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe("MBD-ABCD1234-EFGH5678-IJKL9012");
  await page.getByRole("button", { name: "복사를 마쳤어요" }).click();
  await expect(page.getByRole("heading", { name: "새 초대 코드가 발급됐어요" })).toHaveCount(0);

  const activeInvitation = page.getByRole("listitem").filter({ hasText: "MBD-****7K2P" });
  await activeInvitation.getByRole("button", { name: "MBD-****7K2P 초대 코드 취소" }).click();
  const dialog = page.getByRole("dialog", { name: "MBD-****7K2P 코드를 취소할까요?" });
  await expect(dialog.getByText(/이미 참여한 사용자는 영향을 받지 않으며/)).toBeVisible();
  await dialog.getByRole("button", { name: "코드 취소" }).click();
  await expect(activeInvitation.getByText("취소됨", { exact: true })).toBeVisible();
});

test("uses compact expense filters on mobile", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockExpenseApis(page);
  await page.goto("/expenses");

  await expect(page.getByLabel("상호명 또는 메모 검색")).toBeVisible();
  await expect(page.getByRole("button", { name: "필터", exact: true })).toBeVisible();
  const resultHeading = page.getByRole("heading", { name: "3건 · 60,300원" });
  await expect(resultHeading).toBeVisible();
  const resultPosition = await resultHeading.boundingBox();
  expect(resultPosition?.y).toBeLessThan(600);

  await page.getByRole("button", { name: "필터", exact: true }).click();
  const filterDialog = page.getByRole("dialog", { name: "검색 조건" });
  await expect(filterDialog).toBeVisible();
  await filterDialog.getByRole("button", { name: "모바일 검색 카테고리" }).click();
  const categoryDialog = page.getByRole("dialog", { name: "카테고리 선택" });
  await expect(categoryDialog).toBeVisible();
  await categoryDialog.getByRole("option", { name: "외식" }).click();
  await filterDialog.getByRole("button", { name: "적용" }).click();

  await expect(page.getByRole("button", { name: "필터 1" })).toBeVisible();
  const categoryChip = page.getByRole("button", { name: "카테고리 필터 제거" });
  await expect(categoryChip).toContainText("외식");
  await categoryChip.click();
  await expect(page.getByRole("button", { name: "필터", exact: true })).toBeVisible();
});

test("defaults to the ledger cycle and preserves search when browsing past cycles on mobile", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.clock.setFixedTime(new Date("2026-09-12T12:00:00+09:00"));
  const fixture = await mockExpenseApis(page, true, { budgetCycleStartDay: 25 });
  await page.route("**/api/v1/expenses/initial?*", (route) => route.fulfill({ json: {
    cycleSettings: fixture.ledger, period: { from: "2026-08-25", to: "2026-09-24" }, categories: fixture.categories,
    page: { items: [{ id: "cycle-expense", amount: 12000, spentOn: "2026-09-12", category: null, merchant: "이번 주기 식당", memo: null, version: 0 }], hasNext: false, nextCursor: null, totalAmount: 12000 },
  } }));
  const ranges: URLSearchParams[] = [];
  await page.route("**/api/v1/expenses?*", (route) => {
    const params = new URL(route.request().url()).searchParams;
    ranges.push(params);
    const current = !params.get("from") || params.get("from") === "2026-08-25";
    return route.fulfill({ json: { items: [{ id: "cycle-expense", amount: 12000, spentOn: current ? "2026-09-12" : "2026-08-12", category: null, merchant: current ? "이번 주기 식당" : "이전 주기 식당", memo: null, version: 0 }], hasNext: false, nextCursor: null, totalAmount: 12000 } });
  });
  await page.goto("/expenses");
  const cyclePanel = page.getByRole("region", { name: "식비 주기 선택" });
  await expect(cyclePanel).toContainText("2026.08.25 ~ 2026.09.24");
  await expect(page.getByText("이번 주기 식당", { exact: true })).toBeVisible();
  expect(ranges).toHaveLength(0);
  await page.getByLabel("상호명 또는 메모 검색").fill("식당");
  await page.getByLabel("상호명 또는 메모 검색").press("Enter");
  await expect.poll(() => ranges.at(-1)?.get("keyword")).toBe("식당");
  await page.getByRole("button", { name: "필터", exact: true }).click();
  await page.getByRole("button", { name: "모바일 검색 카테고리" }).click();
  await page.getByRole("option", { name: "외식", exact: true }).click();
  await page.getByRole("dialog", { name: "검색 조건" }).getByRole("button", { name: "적용", exact: true }).click();
  await expect(page.getByRole("button", { name: "이전 주기", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "이전 주기", exact: true }).click();
  await expect(page.getByText("이전 주기 식당", { exact: true })).toBeVisible();
  await expect(page.getByText("이번 주기 식당", { exact: true })).toHaveCount(0);
  expect(ranges.at(-1)?.get("keyword")).toBe("식당");
  expect(ranges.at(-1)?.get("categoryId")).toBe("22222222-2222-2222-2222-222222222222");
  expect(ranges.at(-1)?.get("from")).toBe("2026-07-25");
  await page.getByRole("button", { name: "이번 주기로", exact: true }).click();
  await expect(page.getByText("이번 주기 식당", { exact: true })).toBeVisible();
  await cyclePanel.getByRole("button", { name: /이번 주기 2026/ }).click();
  await page.getByRole("button", { name: "2026.06.25 ~ 2026.07.24", exact: true }).click();
  await expect(cyclePanel).toContainText("2026.06.25 ~ 2026.07.24");
  await cyclePanel.getByRole("button", { name: "2026.06.25 ~ 2026.07.24", exact: true }).click();
  await page.getByRole("button", { name: "전체 기간 보기" }).click();
  await expect.poll(() => ranges.at(-1)?.has("from")).toBe(false);
  await expect(cyclePanel).toContainText("전체 기간");
  await page.getByRole("button", { name: "이번 주기로", exact: true }).click();
  await expect(cyclePanel).toContainText("이번 주기");
  await expect(page.getByRole("button", { name: "이전 주기", exact: true })).toBeEnabled();
  await cyclePanel.getByRole("button", { name: /이번 주기 2026/ }).click();
  await page.getByRole("button", { name: "주기에 포함된 날짜 선택" }).click();
  await page.getByRole("button", { name: "2026년 8월 10일", exact: true }).click();
  await expect(cyclePanel).toContainText("2026.07.25 ~ 2026.08.24");
  await expect(page.getByRole("button", { name: "이번 주기로", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "이번 주기로", exact: true }).click();
  await page.screenshot({ path: path.join(screenshotDirectory, "expense-current-cycle-mobile.png"), fullPage: true });
});

for (const scenario of [
  { name: "weekly Thursday cycle", unit: "WEEKLY", startDay: 1, weekStartDay: 4, today: "2026-09-12", from: "2026-09-10", to: "2026-09-16", previousFrom: "2026-09-03", previousTo: "2026-09-09" },
  { name: "month end across leap February", unit: "MONTHLY", startDay: 31, weekStartDay: 1, today: "2028-03-01", from: "2028-02-29", to: "2028-03-30", previousFrom: "2028-01-31", previousTo: "2028-02-28" },
]) {
  test(`browses ${scenario.name} without gaps`, async ({ page }) => {
    await page.clock.setFixedTime(new Date(`${scenario.today}T12:00:00+09:00`));
    await mockExpenseApis(page, true, { budgetCycleUnit: scenario.unit as "MONTHLY" | "WEEKLY", budgetCycleStartDay: scenario.startDay, budgetWeekStartDay: scenario.weekStartDay });
    await page.goto("/expenses");
    const panel = page.getByRole("region", { name: "식비 주기 선택" });
    await expect(panel).toContainText(`${scenario.from.replaceAll("-", ".")} ~ ${scenario.to.replaceAll("-", ".")}`);
    await expect(page.getByRole("heading", { name: "3건 · 60,300원" })).toBeVisible();
    const response = page.waitForResponse((response) => new URL(response.url()).pathname === "/api/v1/expenses" && new URL(response.url()).searchParams.get("from") === scenario.previousFrom);
    await page.getByRole("button", { name: "이전 주기", exact: true }).click();
    expect(new URL((await response).url()).searchParams.get("to")).toBe(scenario.previousTo);
    await expect(page.getByRole("button", { name: "다음 주기" })).toBeEnabled();
    await page.getByRole("button", { name: "다음 주기" }).click();
    await expect(panel).toContainText("이번 주기");
    await expect(panel).toContainText(scenario.from.replaceAll("-", "."));
  });
}

test("keeps every mobile category reachable on a short screen", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 480 });
  await mockExpenseApis(page);
  await page.goto("/expenses");
  await page.getByRole("button", { name: "필터", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "검색 조건" });
  await dialog.getByRole("button", { name: "모바일 검색 카테고리" }).click();
  const options = page.getByRole("listbox", { name: "모바일 검색 카테고리" });
  await expect(options).toBeVisible();
  await expect(options).toHaveCSS("overflow-y", "auto");
  await options.getByRole("option", { name: "분류 없음" }).click();
  await expect(page.getByRole("dialog", { name: "검색 조건" }).getByRole("button", { name: "모바일 검색 카테고리" })).toContainText("분류 없음");
});

test("upload and delete a profile image", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/settings/account");
  await page.getByLabel("사진 등록").setInputFiles({
    name: "profile.png",
    mimeType: "image/png",
    buffer: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=", "base64"),
  });
  await page.getByRole("button", { name: "크롭 적용" }).click();
  await expect(page.getByText("프로필 사진을 등록했습니다.", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "사진 삭제" }).click();
  await expect(page.getByText("프로필 사진을 삭제했습니다.", { exact: true })).toBeVisible();
});

test("change a password from account settings", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/settings/account");

  await page.getByLabel("현재 비밀번호", { exact: true }).first().fill("password123!");
  await page.getByLabel(/새 비밀번호 8~72자/).fill("new-password123!");
  await page.getByLabel("새 비밀번호 확인").fill("different-password!");
  await page.getByRole("button", { name: "비밀번호 변경" }).click();
  await expect(
    page.getByText("새 비밀번호 확인이 일치하지 않습니다.", { exact: true }),
  ).toBeVisible();

  await page.getByLabel("새 비밀번호 확인").fill("new-password123!");
  await page.getByRole("button", { name: "비밀번호 변경" }).click();
  await expect(page).toHaveURL("/");
});

test("requires explicit confirmation before the last administrator deletes the ledger", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/settings/account");

  await page.getByRole("button", { name: "장부 종료" }).click();
  const dialog = page.getByRole("dialog", { name: "우리집 식비 장부를 종료할까요?" });
  const confirmButton = dialog.getByRole("button", { name: "장부와 계정 삭제" });
  await expect(dialog.getByText(/모든 식비, 예산, 이미지와 멤버 계정/)).toBeVisible();
  await dialog.getByLabel("현재 비밀번호").fill("password123!");
  await expect(confirmButton).toBeDisabled();
  await dialog.getByLabel(/확인을 위해/).fill("우리집 식비 삭제");
  await expect(confirmButton).toBeEnabled();
  await confirmButton.click();
  await expect(page).toHaveURL("/");
});

test("logs out the current device from account settings", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/settings/account");

  await page.getByRole("button", { name: "로그아웃" }).click();
  await expect(page).toHaveURL("/");
});

test("redirects an anonymous user to login and returns to the requested page", async ({ page }) => {
  await mockExpenseApis(page, false);
  await page.goto("/statistics?from=2026-09-01&to=2026-09-30");

  await expect(page).toHaveURL(/\/login\?next=/);
  await page.getByLabel("이메일").fill("himchan@example.com");
  await page.getByLabel("비밀번호", { exact: true }).fill("wrong-password");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page.getByText("이메일 또는 비밀번호가 올바르지 않습니다.", { exact: true })).toBeVisible();

  await page.getByLabel("비밀번호", { exact: true }).fill("password123!");
  await page.getByRole("button", { name: "로그인", exact: true }).click();

  await expect(page).toHaveURL("/statistics?from=2026-09-01&to=2026-09-30");
  await expect(page.getByRole("heading", { name: "기간별 통계" })).toBeVisible();
  await expect(page.getByText("658,800원", { exact: true })).toBeVisible();
});

test("recovers from an invalidated server session", async ({ page }) => {
  await page.context().addCookies([{ name: "MBD_SESSION", value: "expired-session", url: "http://127.0.0.1:3000" }]);
  await page.route("**/api/v1/**", async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    if (pathname === "/api/v1/dashboard") {
      await route.fulfill({ status: 401, json: { detail: "로그인이 필요합니다." } });
    } else if (pathname === "/api/v1/bootstrap/status") {
      await route.fulfill({ json: { available: false } });
    } else {
      await route.fulfill({ json: {} });
    }
  });

  await page.goto("/");
  await expect(page).toHaveURL(/\/login\?expired=1&next=%2F/);
  await expect(page.getByText("로그인 세션이 초기화되었습니다. 다시 로그인해 주세요.", { exact: true })).toBeVisible();
  const cookies = await page.context().cookies();
  expect(cookies.some((cookie) => cookie.name === "MBD_SESSION")).toBe(false);
});

test("joins a shared ledger with the invitation code from the URL", async ({ page }) => {
  await mockExpenseApis(page, false);
  await page.goto("/join?code=MBD-TEST-7K2P");

  await expect(page.getByLabel("초대 코드")).toHaveValue("MBD-TEST-7K2P");
  await page.getByLabel("표시 이름").fill("가족");
  await page.getByLabel("이메일").fill("family@example.com");
  await page.getByLabel("비밀번호", { exact: false }).first().fill("password123!");
  await page.getByLabel("비밀번호 확인").fill("different-password!");
  await page.getByRole("button", { name: "가입하고 시작하기" }).click();
  await expect(page.getByText("비밀번호 확인이 일치하지 않습니다.", { exact: true })).toBeVisible();

  await page.getByLabel("비밀번호 확인").fill("password123!");
  await page.getByRole("button", { name: "가입하고 시작하기" }).click();
  await expect(page).toHaveURL("/");
});

test("creates the first service administrator and ledger", async ({ page }) => {
  await mockExpenseApis(page, false);
  await page.goto("/setup");

  await page.getByLabel("Bootstrap 토큰").fill("test-bootstrap-token");
  await page.getByLabel("표시 이름").fill("힘찬");
  await page.getByLabel("이메일").fill("himchan@example.com");
  await page.getByLabel("비밀번호", { exact: false }).first().fill("password123!");
  await page.getByLabel("비밀번호 확인").fill("password123!");
  await page.getByRole("button", { name: "서비스 관리자 계정 만들기" }).click();

  await expect(page).toHaveURL("/");
  await expect(page.getByRole("heading", { name: "141,200원 남았어요" })).toBeVisible();
});

test("requests a password reset without revealing whether the account exists", async ({ page }) => {
  await mockExpenseApis(page, false);
  await page.goto("/forgot-password");

  await page.getByLabel("이메일").fill("missing@example.com");
  await page.getByRole("button", { name: "재설정 링크 받기" }).click();

  await expect(page.getByText("이메일을 확인해 주세요.", { exact: true })).toBeVisible();
  await expect(page.getByText("가입된 계정이라면 비밀번호 재설정 링크를 보내드렸습니다.", { exact: true })).toBeVisible();
});

test("resets a password with the token from the email", async ({ page }) => {
  await mockExpenseApis(page, false);
  await page.goto("/reset-password?token=test-reset-token");

  await page.getByLabel(/새 비밀번호 8~72자/).fill("recovered-password123!");
  await page.getByLabel("새 비밀번호 확인").fill("different-password!");
  await page.getByRole("button", { name: "비밀번호 재설정" }).click();
  await expect(page.getByText("비밀번호 확인이 일치하지 않습니다.", { exact: true })).toBeVisible();

  await page.getByLabel("새 비밀번호 확인").fill("recovered-password123!");
  await page.getByRole("button", { name: "비밀번호 재설정" }).click();
  await expect(page.getByText("새 비밀번호를 저장했습니다.", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "새 비밀번호로 로그인" })).toHaveAttribute("href", "/login");
});

test("change the shared budget cycle start day", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/settings/budget");

  await page.getByLabel("예산 주기 시작일").fill("25");
  await page.getByRole("button", { name: "예산 주기 저장" }).click();

  await expect(page.getByRole("button", { name: /예산 주기를 변경했습니다/ })).toBeVisible();
  await expect(page.getByLabel("예산 주기 시작일")).toHaveValue("25");
});

test("change the shared push usage threshold", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/settings/notifications");

  await page.getByLabel("Push 기준 사용률").fill("75");
  await page.getByRole("button", { name: "Push 기준 저장" }).click();

  await expect(page.getByRole("button", { name: /Push 기준 사용률을 75%로 변경했습니다/ })).toBeVisible();
  await expect(page.getByLabel("Push 기준 사용률")).toHaveValue("75");
});

async function mockExpenseApis(page: import("@playwright/test").Page, authenticated = true, cycleOverrides: { budgetCycleUnit?: "MONTHLY" | "WEEKLY"; budgetCycleStartDay?: number; budgetWeekStartDay?: number } = {}) {
  if (authenticated) {
    await page.context().addCookies([{ name: "MBD_SESSION", value: "screenshot-session", url: "http://127.0.0.1:3000" }]);
  }
  let categories = [
    { id: "11111111-1111-1111-1111-111111111111", name: "장보기", sortOrder: 1, version: 0 },
    { id: "22222222-2222-2222-2222-222222222222", name: "외식", sortOrder: 2, version: 0 },
    { id: "33333333-3333-3333-3333-333333333333", name: "배달", sortOrder: 3, version: 0 },
    { id: "44444444-4444-4444-4444-444444444444", name: "카페/간식", sortOrder: 4, version: 0 },
    { id: "55555555-5555-5555-5555-555555555555", name: "기타", sortOrder: 5, version: 0 },
  ];
  let members = [
    { id: "77777777-7777-7777-7777-777777777777", displayName: "힘찬", role: "ADMIN" as const, joinedAt: "2026-09-01T10:00:00+09:00", profileImageUrl: null as string | null },
    { id: "88888888-8888-8888-8888-888888888888", displayName: "가족", role: "MEMBER" as const, joinedAt: "2026-09-03T18:30:00+09:00", profileImageUrl: null as string | null },
  ];
  let invitations: Array<{
    id: string;
    maskedCode: string;
    status: "ACTIVE" | "REVOKED";
    useCount: number;
    createdAt: string;
    lastUsedAt: string | null;
  }> = [
    { id: "12121212-1212-1212-1212-121212121212", maskedCode: "MBD-****7K2P", status: "ACTIVE" as const, useCount: 2, createdAt: "2026-09-08T11:00:00+09:00", lastUsedAt: "2026-09-08T12:00:00+09:00" },
    { id: "13131313-1313-1313-1313-131313131313", maskedCode: "MBD-****9R4M", status: "REVOKED" as const, useCount: 1, createdAt: "2026-08-30T09:00:00+09:00", lastUsedAt: "2026-09-01T10:30:00+09:00" },
  ];
  const expenses = [
    { id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", amount: 18500, spentOn: "2026-09-08", category: categories[0], merchant: "동네마트", memo: "주말 장보기", images: [], version: 0, createdAt: "2026-09-08T14:00:00+09:00", updatedAt: "2026-09-08T14:00:00+09:00" },
    { id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", amount: 32000, spentOn: "2026-09-07", category: categories[1], merchant: "을지로 식당", memo: "가족 저녁", images: [], version: 0, createdAt: "2026-09-07T19:00:00+09:00", updatedAt: "2026-09-07T19:00:00+09:00" },
    { id: "cccccccc-cccc-cccc-cccc-cccccccccccc", amount: 9800, spentOn: "2026-09-06", category: categories[3], merchant: "커피하우스", memo: "커피와 간식", images: [], version: 0, createdAt: "2026-09-06T16:00:00+09:00", updatedAt: "2026-09-06T16:00:00+09:00" },
  ];
  let ledger = {
    id: "99999999-9999-9999-9999-999999999999",
    name: "우리집 식비",
    defaultMonthlyBudget: 800000,
    budgetCycleStartDay: 1,
    budgetCycleUnit: "MONTHLY" as "MONTHLY" | "WEEKLY",
    budgetWeekStartDay: 1,
    defaultWeeklyBudget: null as number | null,
    pushUsageThreshold: 80,
    memberCount: 2,
    currentUserRole: "ADMIN" as const,
    version: 0,
    ...cycleOverrides,
  };

  await page.route("**/api/v1/**", async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    if (pathname === "/api/v1/invitations") {
      if (route.request().method() === "POST") {
        const created = {
          id: "14141414-1414-1414-1414-141414141414",
          code: "MBD-ABCD1234-EFGH5678-IJKL9012",
          joinUrl: "http://127.0.0.1:3000/join?code=MBD-ABCD1234-EFGH5678-IJKL9012",
          createdAt: "2026-09-09T14:00:00+09:00",
        };
        invitations = [{ id: created.id, maskedCode: "MBD-****9012", status: "ACTIVE", useCount: 0, createdAt: created.createdAt, lastUsedAt: null }, ...invitations];
        await route.fulfill({ json: created, status: 201 });
      } else {
        const status = new URL(route.request().url()).searchParams.get("status") ?? "ACTIVE";
        await route.fulfill({ json: { items: status === "ALL" ? invitations : invitations.filter((invitation) => invitation.status === status) } });
      }
    } else if (pathname.startsWith("/api/v1/invitations/")) {
      const invitationId = pathname.split("/").at(-1);
      invitations = invitations.map((invitation) => invitation.id === invitationId ? { ...invitation, status: "REVOKED" as const } : invitation);
      await route.fulfill({ status: 204 });
    } else if (pathname === "/api/v1/categories") {
      if (route.request().method() === "POST") {
        const body = route.request().postDataJSON() as { name: string; sortOrder: number };
        const created = { id: "66666666-6666-6666-6666-666666666666", ...body, version: 0 };
        categories = [...categories, created].sort((left, right) => left.sortOrder - right.sortOrder);
        await route.fulfill({ json: created, status: 201 });
      } else {
        await route.fulfill({ json: { items: categories } });
      }
    } else if (pathname.startsWith("/api/v1/categories/")) {
      const categoryId = pathname.split("/").at(-1);
      if (route.request().method() === "PUT") {
        const body = route.request().postDataJSON() as { name: string; sortOrder: number; version: number };
        const updated = { id: categoryId ?? "", name: body.name, sortOrder: body.sortOrder, version: body.version + 1 };
        categories = categories.map((category) => category.id === categoryId ? updated : category)
          .sort((left, right) => left.sortOrder - right.sortOrder);
        await route.fulfill({ json: updated });
      } else if (route.request().method() === "DELETE") {
        categories = categories.filter((category) => category.id !== categoryId);
        await route.fulfill({ status: 204 });
      }
    } else if (pathname === "/api/v1/members") {
      await route.fulfill({ json: { items: members } });
    } else if (pathname.startsWith("/api/v1/members/") && pathname.endsWith("/role")) {
      const memberId = pathname.split("/").at(-2);
      const body = route.request().postDataJSON() as { role: "ADMIN" | "MEMBER" };
      const current = members.find((member) => member.id === memberId);
      const updated = { ...current!, role: body.role };
      members = members.map((member) => member.id === memberId ? updated : member);
      await route.fulfill({ json: updated });
    } else if (pathname === "/api/v1/expenses/initial") {
      const today = await page.evaluate(() => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date()));
      const period = ledger.budgetCycleUnit === "WEEKLY" ? weeklyCycleContaining(today, ledger.budgetWeekStartDay) : budgetCycleContaining(today, ledger.budgetCycleStartDay);
      await route.fulfill({ json: { cycleSettings: ledger, period, categories, page: { items: expenses, nextCursor: null, hasNext: false, totalAmount: expenses.reduce((sum, expense) => sum + expense.amount, 0) } } });
    } else if (pathname === "/api/v1/expenses") {
      await route.fulfill({ json: { items: expenses, nextCursor: null, hasNext: false, totalAmount: expenses.reduce((sum, expense) => sum + expense.amount, 0) } });
    } else if (pathname === "/api/v1/uploads/images") {
      const purpose = new URL(route.request().url()).searchParams.get("purpose") ?? "EXPENSE";
      await route.fulfill({ json: { id: "15151515-1515-1515-1515-151515151515", purpose, contentUrl: "/api/v1/images/15151515-1515-1515-1515-151515151515/content", mimeType: "image/webp", width: 1, height: 1, status: "TEMP" }, status: 201 });
    } else if (pathname === "/api/v1/account/profile-image") {
      if (route.request().method() === "PUT") {
        members[0].profileImageUrl = "/api/v1/images/15151515-1515-1515-1515-151515151515/content";
        await route.fulfill({ json: { profileImageUrl: members[0].profileImageUrl } });
      } else {
        members[0].profileImageUrl = null;
        await route.fulfill({ status: 204 });
      }
    } else if (pathname.startsWith("/api/v1/images/")) {
      await route.fulfill({ body: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=", "base64"), contentType: "image/png" });
    } else if (pathname === "/api/v1/ledger/settings/budget-cycle") {
      const body = route.request().postDataJSON() as { unit: "MONTHLY" | "WEEKLY"; startDay: number; weekStartDay: number; weeklyBudget: number | null; version: number };
      ledger = { ...ledger, budgetCycleUnit: body.unit, budgetCycleStartDay: body.startDay, budgetWeekStartDay: body.weekStartDay, defaultWeeklyBudget: body.weeklyBudget, version: body.version + 1 };
      await route.fulfill({ json: { budgetCycleUnit: ledger.budgetCycleUnit, budgetCycleStartDay: ledger.budgetCycleStartDay, budgetWeekStartDay: ledger.budgetWeekStartDay, defaultWeeklyBudget: ledger.defaultWeeklyBudget, version: ledger.version } });
    } else if (pathname === "/api/v1/ledger/settings/push-threshold") {
      const body = route.request().postDataJSON() as { usageThreshold: number; version: number };
      ledger = { ...ledger, pushUsageThreshold: body.usageThreshold, version: body.version + 1 };
      await route.fulfill({ json: { pushUsageThreshold: ledger.pushUsageThreshold, version: ledger.version } });
    } else if (pathname === "/api/v1/ledger") {
      await route.fulfill({ json: ledger });
    } else if (pathname === "/api/v1/dashboard") {
      await route.fulfill({ json: { yearMonth: "2026-09", cycleUnit: ledger.budgetCycleUnit, period: { from: "2026-09-01", to: "2026-09-30" }, budget: 800000, spent: 658800, remaining: 141200, projectedSpent: 718691, remainingDays: 18, usageRate: 82.4, pushUsageThreshold: ledger.pushUsageThreshold, status: "WARNING", recentExpenses: expenses.map((expense, index) => ({ id: expense.id, amount: expense.amount, spentOn: expense.spentOn, categoryName: expense.category.name, merchant: expense.merchant, version: expense.version, imageUrl: index === 0 ? "/api/v1/images/15151515-1515-1515-1515-151515151515/content" : null, thumbnailUrl: index === 0 ? "/api/v1/images/15151515-1515-1515-1515-151515151515/thumbnail" : null })) } });
    } else if (pathname === "/api/v1/statistics" || pathname === "/api/v1/statistics/initial") {
      const statistics = { period: { from: "2026-09-01", to: "2026-09-30" }, totalAmount: 658800, budget: { amount: 800000, usageRate: 82.4 }, comparison: { from: "2026-08-01", to: "2026-08-31", totalAmount: 592000, changeAmount: 66800, changeRate: 11.3 }, daily: [{ date: "2026-09-02", amount: 44000 }, { date: "2026-09-05", amount: 78000 }, { date: "2026-09-08", amount: 60300 }, { date: "2026-09-12", amount: 125000 }, { date: "2026-09-18", amount: 89000 }, { date: "2026-09-24", amount: 142000 }, { date: "2026-09-29", amount: 120500 }], categories: [{ categoryId: categories[0].id, categoryName: "장보기", amount: 283000, ratio: 43 }, { categoryId: categories[1].id, categoryName: "외식", amount: 197600, ratio: 30 }, { categoryId: categories[2].id, categoryName: "배달", amount: 112000, ratio: 17 }, { categoryId: categories[3].id, categoryName: "카페/간식", amount: 66200, ratio: 10 }] };
      if (pathname.endsWith("/initial")) {
        const today = await page.evaluate(() => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date()));
        const cycle = ledger.budgetCycleUnit === "WEEKLY" ? weeklyCycleContaining(today, ledger.budgetWeekStartDay) : budgetCycleContaining(today, ledger.budgetCycleStartDay);
        statistics.period = { from: cycle.from, to: cycle.to };
        await route.fulfill({ json: { cycleSettings: ledger, statistics } });
      } else {
        await route.fulfill({ json: statistics });
      }
    } else if (pathname.startsWith("/api/v1/budgets/")) {
      const yearMonth = pathname.split("/").at(-1);
      const period = yearMonth === "current" ? weeklyCycleContaining("2026-09-12", ledger.budgetWeekStartDay) : budgetCycleStarting(yearMonth!, ledger.budgetCycleStartDay);
      await route.fulfill({ json: { yearMonth: period.yearMonth, period: { from: period.from, to: period.to }, amount: yearMonth === "current" ? ledger.defaultWeeklyBudget : 800000, source: yearMonth === "current" ? "WEEKLY_DEFAULT" : "DEFAULT", version: 0 } });
    } else if (pathname === "/api/v1/bootstrap/status") {
      await route.fulfill({ json: { available: true } });
    } else if (pathname === "/api/v1/auth/login") {
      const body = route.request().postDataJSON() as { email: string; password: string };
      if (body.password === "wrong-password") {
        await route.fulfill({ json: { detail: "이메일 또는 비밀번호가 올바르지 않습니다." }, status: 401 });
      } else {
        await route.fulfill({
          headers: { "set-cookie": "MBD_SESSION=authenticated-session; Path=/; HttpOnly; SameSite=Lax" },
          json: { id: members[0].id, email: body.email, displayName: "힘찬", serviceRole: "SERVICE_ADMIN", ledgerRole: "ADMIN", profileImageUrl: null },
        });
      }
    } else if (pathname === "/api/v1/auth/register-ledger") {
      const body = route.request().postDataJSON() as { email: string; displayName: string };
      await route.fulfill({
        headers: { "set-cookie": "MBD_SESSION=ledger-owner-session; Path=/; HttpOnly; SameSite=Lax" },
        json: { id: "99999999-aaaa-bbbb-cccc-111111111111", email: body.email, displayName: body.displayName, serviceRole: "USER", ledgerRole: "ADMIN", profileImageUrl: null },
        status: 201,
      });
    } else if (pathname === "/api/v1/auth/register") {
      const body = route.request().postDataJSON() as { inviteCode: string; email: string; displayName: string };
      await route.fulfill({
        headers: { "set-cookie": "MBD_SESSION=registered-session; Path=/; HttpOnly; SameSite=Lax" },
        json: { id: members[1].id, email: body.email, displayName: body.displayName, serviceRole: "USER", ledgerRole: "MEMBER", profileImageUrl: null },
        status: 201,
      });
    } else if (pathname === "/api/v1/auth/password-reset-requests") {
      await route.fulfill({ status: 202 });
    } else if (pathname === "/api/v1/auth/password-resets") {
      const body = route.request().postDataJSON() as { token: string };
      if (body.token !== "test-reset-token") {
        await route.fulfill({ json: { detail: "비밀번호 재설정 링크가 유효하지 않거나 만료되었습니다." }, status: 400 });
      } else {
        await route.fulfill({
          headers: { "set-cookie": "MBD_SESSION=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax" },
          status: 204,
        });
      }
    } else if (pathname === "/api/v1/bootstrap/admin") {
      if (route.request().headers()["x-bootstrap-token"] !== "test-bootstrap-token") {
        await route.fulfill({ json: { detail: "요청한 리소스를 찾을 수 없습니다." }, status: 404 });
      } else {
        await route.fulfill({
          headers: { "set-cookie": "MBD_SESSION=bootstrap-session; Path=/; HttpOnly; SameSite=Lax" },
          json: { id: members[0].id, email: "himchan@example.com", displayName: "힘찬", serviceRole: "SERVICE_ADMIN", ledgerRole: "ADMIN", profileImageUrl: null },
          status: 201,
        });
      }
    } else if (pathname === "/api/v1/auth/csrf") {
      await route.fulfill({ json: { headerName: "X-XSRF-TOKEN", token: "screenshot-token" } });
    } else if (pathname === "/api/v1/auth/me") {
      await route.fulfill({ json: { id: members[0].id, email: "himchan@example.com", displayName: "힘찬", serviceRole: "SERVICE_ADMIN", ledgerRole: "ADMIN", profileImageUrl: members[0].profileImageUrl } });
    } else {
      await route.fulfill({ status: 204 });
    }
  });
  return { ledger, categories, expenses };
}

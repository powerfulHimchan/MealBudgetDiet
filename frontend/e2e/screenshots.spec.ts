import { expect, test } from "@playwright/test";
import path from "node:path";
import { budgetCycleStarting } from "../src/lib/budget-cycle";

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
    { route: "/settings/budget", file: "budget-settings-mobile.png", heading: "예산 관리" },
    { route: "/settings/notifications", file: "notification-settings-mobile.png", heading: "푸시 알림" },
    { route: "/settings/categories", file: "category-settings-mobile.png", heading: "카테고리 관리" },
    { route: "/settings/members", file: "member-settings-mobile.png", heading: "참여자 관리" },
    { route: "/settings/invitations", file: "invitation-settings-mobile.png", heading: "초대 코드" },
    { route: "/settings/account", file: "account-settings-mobile.png", heading: "계정 설정" },
    { route: "/offline", file: "offline-mobile.png", heading: "인터넷 연결이 필요해요" },
  ];

  for (const screen of screens) {
    await mobile.goto(screen.route);
    await expect(mobile.getByRole("heading", { level: 1, name: screen.heading })).toBeVisible();
    if (screen.route === "/expenses") {
      await expect(mobile.getByText("동네마트", { exact: true })).toBeVisible();
    } else if (screen.route === "/statistics") {
      await expect(mobile.getByText("658,800원", { exact: true })).toBeVisible();
    } else if (screen.route === "/settings") {
      await expect(mobile.getByRole("link", { name: /예산 관리/ })).toBeVisible();
      await expect(mobile.getByRole("link", { name: /푸시 알림/ })).toBeVisible();
      await expect(mobile.getByRole("link", { name: /계정 설정/ })).toBeVisible();
    } else if (screen.route === "/settings/budget") {
      await expect(mobile.getByText("현재 적용 금액", { exact: true })).toBeVisible();
      await expect(mobile.getByRole("heading", { name: "예산 주기" })).toBeVisible();
    } else if (screen.route === "/settings/notifications") {
      await expect(mobile.getByRole("button", { name: "이 기기 알림 활성화" })).toBeVisible();
    } else if (screen.route === "/settings/categories") {
      await expect(mobile.getByRole("heading", { name: "사용 중인 카테고리" })).toBeVisible();
      await expect(mobile.getByRole("button", { name: "새 카테고리 추가" })).toBeVisible();
      await expect(mobile.getByText("장보기", { exact: true })).toBeVisible();
    } else if (screen.route === "/settings/members") {
      await expect(mobile.getByRole("heading", { name: "우리집 식비 참여자" })).toBeVisible();
      await expect(mobile.getByText("힘찬", { exact: true })).toBeVisible();
      await expect(mobile.getByText("가족", { exact: true })).toBeVisible();
    } else if (screen.route === "/settings/invitations") {
      await expect(mobile.getByRole("heading", { name: "발급한 초대 코드" })).toBeVisible();
      await expect(mobile.getByRole("button", { name: "새 초대 코드 발급" })).toBeVisible();
      await expect(mobile.getByText("MBD-****7K2P", { exact: true })).toBeVisible();
    } else if (screen.route === "/settings/account") {
      await expect(mobile.getByRole("heading", { name: "프로필 사진" })).toBeVisible();
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
  }

  await mobile.close();
});

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

  await familyRow.getByRole("button", { name: "일반 참여자로 변경" }).click();
  dialog = page.getByRole("dialog", { name: "가족 님을 일반 참여자로 변경할까요?" });
  await dialog.getByRole("button", { name: "권한 변경" }).click();
  await expect(familyRow.getByText("일반 참여자", { exact: true })).toBeVisible();

  const currentUserRow = page.getByRole("listitem").filter({ hasText: "힘찬" });
  await expect(currentUserRow.getByRole("button", { name: "일반 참여자로 변경" })).toBeDisabled();
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

test("upload and delete a profile image", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/settings/account");
  await page.getByLabel("사진 등록").setInputFiles({
    name: "profile.png",
    mimeType: "image/png",
    buffer: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=", "base64"),
  });
  await expect(page.getByText("프로필 사진을 등록했습니다.", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "사진 삭제" }).click();
  await expect(page.getByText("프로필 사진을 삭제했습니다.", { exact: true })).toBeVisible();
});

test("change the shared budget cycle start day", async ({ page }) => {
  await mockExpenseApis(page);
  await page.goto("/settings/budget");

  await page.getByLabel("예산 주기 시작일").fill("25");
  await page.getByRole("button", { name: "예산 주기 저장" }).click();

  await expect(page.getByRole("button", { name: /예산 주기 시작일을 변경했습니다/ })).toBeVisible();
  await expect(page.getByLabel("예산 주기 시작일")).toHaveValue("25");
});

async function mockExpenseApis(page: import("@playwright/test").Page) {
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
    memberCount: 2,
    currentUserRole: "ADMIN" as const,
    version: 0,
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
    } else if (pathname === "/api/v1/expenses") {
      await route.fulfill({ json: { items: expenses, nextCursor: null, hasNext: false } });
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
      const body = route.request().postDataJSON() as { startDay: number; version: number };
      ledger = { ...ledger, budgetCycleStartDay: body.startDay, version: body.version + 1 };
      await route.fulfill({ json: { budgetCycleStartDay: ledger.budgetCycleStartDay, version: ledger.version } });
    } else if (pathname === "/api/v1/ledger") {
      await route.fulfill({ json: ledger });
    } else if (pathname === "/api/v1/dashboard") {
      await route.fulfill({ json: { yearMonth: "2026-09", period: { from: "2026-09-01", to: "2026-09-30" }, budget: 800000, spent: 658800, remaining: 141200, usageRate: 82.4, status: "WARNING", recentExpenses: expenses.map((expense) => ({ id: expense.id, amount: expense.amount, spentOn: expense.spentOn, categoryName: expense.category.name, merchant: expense.merchant, version: expense.version })) } });
    } else if (pathname === "/api/v1/statistics") {
      await route.fulfill({ json: { period: { from: "2026-09-01", to: "2026-09-30" }, totalAmount: 658800, budget: { amount: 800000, usageRate: 82.4 }, comparison: { from: "2026-08-01", to: "2026-08-31", totalAmount: 592000, changeAmount: 66800, changeRate: 11.3 }, daily: [{ date: "2026-09-02", amount: 44000 }, { date: "2026-09-05", amount: 78000 }, { date: "2026-09-08", amount: 60300 }, { date: "2026-09-12", amount: 125000 }, { date: "2026-09-18", amount: 89000 }, { date: "2026-09-24", amount: 142000 }, { date: "2026-09-29", amount: 120500 }], categories: [{ categoryId: categories[0].id, categoryName: "장보기", amount: 283000, ratio: 43 }, { categoryId: categories[1].id, categoryName: "외식", amount: 197600, ratio: 30 }, { categoryId: categories[2].id, categoryName: "배달", amount: 112000, ratio: 17 }, { categoryId: categories[3].id, categoryName: "카페/간식", amount: 66200, ratio: 10 }] } });
    } else if (pathname.startsWith("/api/v1/budgets/")) {
      const yearMonth = pathname.split("/").at(-1);
      const period = budgetCycleStarting(yearMonth!, ledger.budgetCycleStartDay);
      await route.fulfill({ json: { yearMonth, period: { from: period.from, to: period.to }, amount: 800000, source: "DEFAULT", version: 0 } });
    } else if (pathname === "/api/v1/auth/csrf") {
      await route.fulfill({ json: { headerName: "X-XSRF-TOKEN", token: "screenshot-token" } });
    } else if (pathname === "/api/v1/auth/me") {
      await route.fulfill({ json: { id: members[0].id, email: "himchan@example.com", displayName: "힘찬", role: "ADMIN", profileImageUrl: members[0].profileImageUrl } });
    } else {
      await route.fulfill({ status: 204 });
    }
  });
}

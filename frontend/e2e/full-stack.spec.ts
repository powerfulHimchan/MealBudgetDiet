import { expect, test, type Page } from "@playwright/test";

const bootstrapToken = "e2e-bootstrap-token-only-for-ci";
const adminEmail = "admin.e2e@example.com";
const memberEmail = "member.e2e@example.com";
const password = "E2e-password123!";
const originalMerchant = "E2E 식당";
const updatedMerchant = "E2E 가족식당";

const receiptPng = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
  "base64",
);

function todayInSeoul() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date());
}

async function createFirstAdmin(page: Page) {
  await page.goto("/setup");
  await expect(page.getByRole("heading", { name: "첫 장부 만들기" })).toBeVisible();

  const form = page.locator("form.auth-form");
  await form.getByLabel("Bootstrap 토큰").fill(bootstrapToken);
  await form.getByLabel("장부 이름").fill("E2E 공유 식비");
  await form.getByLabel("기본 월 예산").fill("500000");
  await form.getByLabel("표시 이름").fill("E2E 관리자");
  await form.getByLabel("이메일").fill(adminEmail);
  await form.getByLabel(/^비밀번호 8~72자$/).fill(password);
  await form.getByLabel("비밀번호 확인").fill(password);

  const responsePromise = page.waitForResponse((response) =>
    response.url().endsWith("/api/v1/bootstrap/admin") && response.request().method() === "POST");
  await form.getByRole("button", { name: "서비스 관리자 계정 만들기" }).click();
  expect((await responsePromise).status()).toBe(201);
  await expect(page).toHaveURL("/");
  await expect(page.getByRole("heading", { name: "500,000원 남았어요" })).toBeVisible();
}

async function login(page: Page) {
  await page.goto("/login");
  const form = page.locator("form.auth-form");
  await form.getByLabel("이메일").fill(adminEmail);
  await form.getByLabel("비밀번호", { exact: true }).fill(password);

  const responsePromise = page.waitForResponse((response) =>
    response.url().endsWith("/api/v1/auth/login") && response.request().method() === "POST");
  await form.getByRole("button", { name: "로그인" }).click();
  expect((await responsePromise).status()).toBe(200);
  await expect(page).toHaveURL("/");
  await expect(page.getByRole("heading", { name: "500,000원 남았어요" })).toBeVisible();
}

test("two users share an expense with an image and see matching statistics", async ({ browser }) => {
  const bootstrapContext = await browser.newContext();
  await createFirstAdmin(await bootstrapContext.newPage());
  await bootstrapContext.close();

  const adminContext = await browser.newContext();
  const adminPage = await adminContext.newPage();
  await login(adminPage);

  await adminPage.goto("/settings/invitations");
  await expect(adminPage.getByRole("heading", { name: "초대 코드" })).toBeVisible();
  const invitationResponsePromise = adminPage.waitForResponse((response) =>
    response.url().endsWith("/api/v1/invitations") && response.request().method() === "POST");
  await adminPage.getByRole("button", { name: "새 초대 코드 발급" }).click();
  const invitationResponse = await invitationResponsePromise;
  expect(invitationResponse.status()).toBe(201);
  const invitation = await invitationResponse.json() as { code: string };
  await expect(adminPage.getByText(invitation.code, { exact: true })).toBeVisible();

  const memberContext = await browser.newContext();
  const memberPage = await memberContext.newPage();
  await memberPage.goto(`/join?code=${encodeURIComponent(invitation.code)}`);
  const joinForm = memberPage.locator("form.auth-form");
  await joinForm.getByLabel("표시 이름").fill("E2E 참여자");
  await joinForm.getByLabel("이메일").fill(memberEmail);
  await joinForm.getByLabel(/^비밀번호 8~72자$/).fill(password);
  await joinForm.getByLabel("비밀번호 확인").fill(password);
  const registrationResponsePromise = memberPage.waitForResponse((response) =>
    response.url().endsWith("/api/v1/auth/register") && response.request().method() === "POST");
  await joinForm.getByRole("button", { name: "가입하고 시작하기" }).click();
  expect((await registrationResponsePromise).status()).toBe(201);
  await expect(memberPage).toHaveURL("/");
  await expect(memberPage.getByRole("heading", { name: "500,000원 남았어요" })).toBeVisible();

  await adminPage.goto("/expenses");
  await adminPage.getByRole("button", { name: "식비 등록" }).click();
  const createDialog = adminPage.getByRole("dialog", { name: "식비 등록" });
  await createDialog.getByLabel("금액").fill("45000");
  await createDialog.getByLabel("사용 날짜").fill(todayInSeoul());
  await createDialog.getByLabel("카테고리").selectOption({ label: "외식" });
  await createDialog.getByLabel("상호명 선택").fill(originalMerchant);
  await createDialog.getByLabel("메모 선택").fill("두 사용자가 함께 확인하는 실제 E2E 데이터");

  const uploadResponsePromise = adminPage.waitForResponse((response) =>
    response.url().includes("/api/v1/uploads/images?purpose=EXPENSE")
      && response.request().method() === "POST");
  await createDialog.getByLabel("이미지 추가").setInputFiles({
    name: "e2e-receipt.png",
    mimeType: "image/png",
    buffer: receiptPng,
  });
  expect((await uploadResponsePromise).status()).toBe(201);
  await expect(createDialog.getByRole("img", { name: "첨부 이미지 1" })).toBeVisible();

  const createExpenseResponsePromise = adminPage.waitForResponse((response) =>
    response.url().endsWith("/api/v1/expenses") && response.request().method() === "POST");
  await createDialog.getByRole("button", { name: "등록하기" }).click();
  expect((await createExpenseResponsePromise).status()).toBe(201);
  await expect(adminPage.getByText("식비 내역을 등록했습니다.", { exact: true })).toBeVisible();
  await expect(adminPage.getByText(originalMerchant, { exact: true })).toBeVisible();

  await memberPage.goto("/expenses");
  const memberExpense = memberPage.getByRole("listitem").filter({ hasText: originalMerchant });
  await expect(memberExpense).toBeVisible();
  const sharedThumbnail = memberExpense.locator("img.expense-thumbnail");
  await expect(sharedThumbnail).toBeVisible();
  await expect.poll(() => sharedThumbnail.evaluate((image: HTMLImageElement) =>
    image.complete && image.naturalWidth > 0)).toBe(true);

  await memberExpense.getByRole("button", { name: `${originalMerchant} 수정` }).click();
  const updateDialog = memberPage.getByRole("dialog", { name: "식비 수정" });
  await updateDialog.getByLabel("금액").fill("54321");
  await updateDialog.getByLabel("상호명 선택").fill(updatedMerchant);
  await updateDialog.getByLabel("메모 선택").fill("참여자가 금액과 상호명을 수정함");
  const updateResponsePromise = memberPage.waitForResponse((response) =>
    /\/api\/v1\/expenses\/[^/]+$/.test(new URL(response.url()).pathname)
      && response.request().method() === "PUT");
  await updateDialog.getByRole("button", { name: "수정 저장" }).click();
  expect((await updateResponsePromise).status()).toBe(200);
  await expect(memberPage.getByText("식비 내역을 수정했습니다.", { exact: true })).toBeVisible();
  await expect(memberPage.getByText(updatedMerchant, { exact: true })).toBeVisible();

  await adminPage.reload();
  const updatedAdminExpense = adminPage.getByRole("listitem").filter({ hasText: updatedMerchant });
  await expect(updatedAdminExpense).toBeVisible();
  await expect(updatedAdminExpense.getByText("-54,321원", { exact: true })).toBeVisible();

  await adminPage.goto("/statistics");
  await expect(adminPage.getByRole("heading", { name: "기간별 통계" })).toBeVisible();
  const summary = adminPage.getByRole("region", { name: "통계 요약" });
  await expect(summary.locator("article").first().getByText("54,321원", { exact: true })).toBeVisible();
  const restaurantCategory = adminPage.locator(".category-bars li").filter({ hasText: "외식" });
  await expect(restaurantCategory.getByText("54,321원", { exact: true })).toBeVisible();

  await memberPage.goto("/expenses");
  const deletableExpense = memberPage.getByRole("listitem").filter({ hasText: updatedMerchant });
  memberPage.once("dialog", (dialog) => dialog.accept());
  const deleteResponsePromise = memberPage.waitForResponse((response) =>
    /\/api\/v1\/expenses\/[^/]+$/.test(new URL(response.url()).pathname)
      && response.request().method() === "DELETE");
  await deletableExpense.getByRole("button", { name: `${updatedMerchant} 삭제` }).click();
  expect((await deleteResponsePromise).status()).toBe(204);
  await expect(memberPage.getByText("식비 내역을 삭제했습니다.", { exact: true })).toBeVisible();
  await expect(memberPage.getByText(updatedMerchant, { exact: true })).toHaveCount(0);

  await adminPage.goto("/");
  await expect(adminPage.getByRole("heading", { name: "500,000원 남았어요" })).toBeVisible();
  await expect(adminPage.getByText(updatedMerchant, { exact: true })).toHaveCount(0);

  await memberContext.close();
  await adminContext.close();
});

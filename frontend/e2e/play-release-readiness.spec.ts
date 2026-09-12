import { expect, test } from "@playwright/test";

test.describe("Google Play 공개 표면", () => {
  test("로그인 없이 개인정보처리방침과 계정 삭제 안내를 조회한다", async ({ page }) => {
    await page.goto("/privacy");
    await expect(page).toHaveTitle("개인정보처리방침 | Sikbi - 함께 쓰는 식비 관리");
    await expect(page.getByRole("heading", { level: 1, name: "개인정보처리방침" })).toBeVisible();
    await expect(page.getByRole("link", { name: "계정 삭제 안내" }).first()).toHaveAttribute("href", "/account-deletion");

    await page.goto("/account-deletion");
    await expect(page).toHaveTitle("계정 삭제 안내 | Sikbi - 함께 쓰는 식비 관리");
    await expect(page.getByRole("heading", { level: 1, name: "Sikbi 계정 삭제" })).toBeVisible();
    const deletionLink = page.getByRole("link", { name: "로그인하고 계정 삭제" });
    await expect(deletionLink).toHaveAttribute("href", "/settings/account");
    await deletionLink.click();
    await expect(page).toHaveURL(/\/login\?next=%2Fsettings%2Faccount$/);
  });

  test("TWA가 사용할 PWA manifest와 PNG 아이콘을 제공한다", async ({ request }) => {
    const logoResponse = await request.get("/icon.svg");
    expect(logoResponse.ok()).toBeTruthy();
    expect(await logoResponse.text()).toContain("영수증과 숟가락, 포크");

    const manifestResponse = await request.get("/manifest.webmanifest");
    expect(manifestResponse.ok()).toBeTruthy();
    const manifest = await manifestResponse.json();

    expect(manifest).toMatchObject({
      id: "/",
      name: "Sikbi - 함께 쓰는 식비 관리",
      start_url: "/",
      scope: "/",
      display: "standalone",
    });
    expect(manifest.icons).toEqual(expect.arrayContaining([
      expect.objectContaining({ src: "/icons/icon-192.png", sizes: "192x192" }),
      expect.objectContaining({ src: "/icons/icon-512.png", sizes: "512x512" }),
      expect.objectContaining({ src: "/icons/icon-maskable-512.png", purpose: "maskable" }),
      expect.objectContaining({ src: "/icons/icon-badge-96.png", purpose: "monochrome" }),
    ]));

    for (const iconUrl of ["/icons/icon-192.png", "/icons/icon-512.png", "/icons/icon-maskable-512.png", "/icons/icon-badge-96.png"]) {
      const iconResponse = await request.get(iconUrl);
      expect(iconResponse.ok()).toBeTruthy();
      expect(iconResponse.headers()["content-type"]).toContain("image/png");
    }
  });
});

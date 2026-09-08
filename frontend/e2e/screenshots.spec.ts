import { expect, test } from "@playwright/test";
import path from "node:path";

const screenshotDirectory = path.resolve(process.cwd(), "artifacts/screenshots");

test("capture implemented frontend screens", async ({ browser }) => {
  const desktop = await browser.newPage({ viewport: { width: 1440, height: 900 } });
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
    await mobile.screenshot({
      path: path.join(screenshotDirectory, screen.file),
      fullPage: true,
      animations: "disabled",
    });
  }

  await mobile.close();
});

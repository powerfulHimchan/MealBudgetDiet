import type { Metadata, Viewport } from "next";
import { PwaRegister } from "./pwa-register";
import "./globals.css";

export const metadata: Metadata = {
  title: "MealBudgetDiet",
  description: "공용 식비와 월 예산을 함께 관리하세요.",
  applicationName: "MealBudgetDiet",
  appleWebApp: { capable: true, statusBarStyle: "black-translucent", title: "MealBudgetDiet" },
};

export const viewport: Viewport = { themeColor: "#080d18", colorScheme: "light" };

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko">
      <body><PwaRegister />{children}</body>
    </html>
  );
}

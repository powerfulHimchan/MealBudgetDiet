import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "MealBudgetDiet",
    short_name: "MealBudget",
    description: "공용 식비와 월 예산을 함께 관리하세요.",
    start_url: "/",
    display: "standalone",
    background_color: "#f2f6fc",
    theme_color: "#080d18",
    lang: "ko",
    icons: [{ src: "/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" }],
  };
}

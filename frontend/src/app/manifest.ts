import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    id: "/",
    name: "식비",
    short_name: "식비",
    description: "공용 식비와 월 예산을 함께 관리하세요.",
    start_url: "/",
    scope: "/",
    display: "standalone",
    orientation: "any",
    background_color: "#f2f6fc",
    theme_color: "#080d18",
    lang: "ko",
    categories: ["finance", "lifestyle", "productivity"],
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icons/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
      { src: "/icons/icon-badge-96.png", sizes: "96x96", type: "image/png", purpose: "monochrome" },
    ],
  };
}

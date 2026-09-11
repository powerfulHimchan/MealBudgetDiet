import type { Metadata } from "next";
import { AuthScreen } from "../auth-screen";

export const metadata: Metadata = { title: "첫 장부 설정 | Sikbi - 함께 쓰는 식비 관리" };

export default function SetupPage() {
  return <AuthScreen mode="setup" />;
}

import type { Metadata } from "next";
import { AuthScreen } from "../auth-screen";

export const metadata: Metadata = { title: "새 장부 만들기 | Sikbi - 함께 쓰는 식비 관리" };

export default function SignupPage() {
  return <AuthScreen mode="signup" />;
}

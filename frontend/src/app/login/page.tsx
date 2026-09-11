import type { Metadata } from "next";
import { AuthScreen } from "../auth-screen";

export const metadata: Metadata = { title: "로그인 | Sikbi - 함께 쓰는 식비 관리" };

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ expired?: string | string[]; next?: string | string[] }>;
}) {
  const params = await searchParams;
  return (
    <AuthScreen
      mode="login"
      nextPath={typeof params.next === "string" ? params.next : undefined}
      sessionExpired={params.expired === "1"}
    />
  );
}

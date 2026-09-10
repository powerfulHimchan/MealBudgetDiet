import type { Metadata } from "next";
import { AuthScreen } from "../auth-screen";

export const metadata: Metadata = { title: "로그인 | MealBudgetDiet" };

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[] }>;
}) {
  const params = await searchParams;
  return <AuthScreen mode="login" nextPath={typeof params.next === "string" ? params.next : undefined} />;
}

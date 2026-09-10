import type { Metadata } from "next";
import { AuthScreen } from "../auth-screen";

export const metadata: Metadata = { title: "초대 가입 | MealBudgetDiet" };

export default async function JoinPage({
  searchParams,
}: {
  searchParams: Promise<{ code?: string | string[] }>;
}) {
  const params = await searchParams;
  return <AuthScreen initialInviteCode={typeof params.code === "string" ? params.code : undefined} mode="join" />;
}

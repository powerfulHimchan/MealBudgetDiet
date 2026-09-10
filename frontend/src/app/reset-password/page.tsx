import { AuthScreen } from "../auth-screen";

type ResetPasswordPageProps = {
  searchParams: Promise<{ token?: string }>;
};

export default async function ResetPasswordPage({ searchParams }: ResetPasswordPageProps) {
  const { token = "" } = await searchParams;
  return <AuthScreen initialResetToken={token} mode="reset" />;
}

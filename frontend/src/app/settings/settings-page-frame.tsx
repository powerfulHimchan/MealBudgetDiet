import { ArrowLeft, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { AppNav } from "../app-nav";

type SettingsPageFrameProps = {
  badge?: ReactNode;
  children: ReactNode;
  description: string;
  eyebrow: string;
  showBackLink?: boolean;
  title: string;
};

export function SettingsPageFrame({
  badge,
  children,
  description,
  eyebrow,
  showBackLink = false,
  title,
}: SettingsPageFrameProps) {
  return (
    <main className="app-shell settings-shell">
      <header className="topbar">
        <Link className="brand" href="/"><span className="brand-mark">M</span><span>MealBudgetDiet</span></Link>
        {badge ?? <span className="page-badge"><SlidersHorizontal size={16} /> 설정 메뉴</span>}
      </header>

      {showBackLink && <Link className="settings-back-link" href="/settings"><ArrowLeft size={16} /> 설정으로 돌아가기</Link>}

      <section className="page-hero">
        <div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p></div>
      </section>

      {children}
      <AppNav active="settings" />
    </main>
  );
}
